package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.common.ratelimit.RateLimiterService;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTokenVerifyResponse;
import com.yem.hlm.backend.portal.domain.PortalToken;
import com.yem.hlm.backend.portal.repo.PortalTokenRepository;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manages the portal magic-link authentication flow.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Client calls {@code requestLink(email, tenantKey)}.
 *       A random 32-byte token is generated; only its SHA-256 hex hash is stored.
 *       The raw token is embedded in a magic-link URL and sent by email.</li>
 *   <li>Client clicks the link; frontend calls {@code verifyToken(rawToken)}.
 *       Service hashes the raw token, looks up the record, validates it,
 *       marks it used (one-time), and returns a 2-h portal JWT.</li>
 * </ol>
 */
@Service
@Transactional
public class PortalAuthService {

    private static final long TOKEN_TTL_HOURS = 48;
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final ContactRepository contactRepository;
    private final PortalTokenRepository portalTokenRepository;
    private final PortalJwtProvider portalJwtProvider;
    private final EmailSender emailSender;
    private final RateLimiterService rateLimiterService;

    /** Base URL used to build the magic-link. Override via {@code app.portal.base-url}. */
    @Value("${app.portal.base-url:http://localhost:4200}")
    private String portalBaseUrl;

    public PortalAuthService(TenantRepository tenantRepository,
                             ContactRepository contactRepository,
                             PortalTokenRepository portalTokenRepository,
                             PortalJwtProvider portalJwtProvider,
                             EmailSender emailSender,
                             RateLimiterService rateLimiterService) {
        this.tenantRepository      = tenantRepository;
        this.contactRepository     = contactRepository;
        this.portalTokenRepository = portalTokenRepository;
        this.portalJwtProvider     = portalJwtProvider;
        this.emailSender           = emailSender;
        this.rateLimiterService    = rateLimiterService;
    }

    // =========================================================================
    // Request link
    // =========================================================================

    /**
     * Generates a magic link and sends it by email.
     *
     * <p>Always returns a 200 with a generic message even if the email is not
     * found — to prevent user enumeration. The {@code magicLinkUrl} is included
     * in the response body for dev/test convenience; production clients should
     * not surface this URL to users (they should wait for the email).
     *
     * @param email     buyer's email address
     * @param tenantKey the tenant's functional key (e.g. "acme")
     * @return response with magic-link URL
     */
    public MagicLinkResponse requestLink(String email, String tenantKey) {
        rateLimiterService.checkPortalLink(email.trim().toLowerCase());

        var tenant = tenantRepository.findByKey(tenantKey)
                .orElseThrow(() -> new PortalTokenInvalidException("Unknown tenant or email"));

        var contactOpt = contactRepository.findByTenant_IdAndEmailIgnoreCase(tenant.getId(), email);
        if (contactOpt.isEmpty()) {
            // Unknown email — return generic 200 to prevent user enumeration
            return new MagicLinkResponse("Magic link sent to " + email, "");
        }
        Contact contact = contactOpt.get();

        // Generate raw token (32 bytes → URL-safe base64, ~43 chars, no padding)
        byte[] rawBytes = new byte[32];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        // Store only the SHA-256 hash
        String tokenHash = sha256Hex(rawToken);

        Instant expiresAt = Instant.now().plusSeconds(TOKEN_TTL_HOURS * 3600);
        var portalToken = new PortalToken(tenant, contact, tokenHash, expiresAt);
        portalTokenRepository.save(portalToken);

        // Build magic-link URL
        String magicLinkUrl = portalBaseUrl + "/portal/verify?token=" + rawToken;

        // Build HTML email body
        String tenantName = tenant.getName();
        String subject = "Votre lien d'accès au portail — " + tenantName;
        String html = """
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                <h2 style="color:#1F3864">Bienvenue sur votre espace personnel</h2>
                <p>Bonjour,</p>
                <p>Cliquez sur le bouton ci-dessous pour accéder à vos contrats et documents :</p>
                <p style="text-align:center;margin:32px 0">
                  <a href="%s" style="background:#2E75B6;color:#fff;padding:14px 28px;
                     border-radius:4px;text-decoration:none;font-size:16px">
                    Accéder à mon espace
                  </a>
                </p>
                <p style="color:#666;font-size:13px">
                  Ce lien est valable <strong>48 heures</strong> et ne peut être utilisé
                  qu'une seule fois. Si vous n'avez pas demandé cet accès, ignorez cet email.
                </p>
                <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                <p style="color:#999;font-size:11px">%s &#8212; Espace Client</p>
                </body></html>
                """.formatted(magicLinkUrl, tenantName);

        // Send email (fire-and-forget; NoopEmailSender used in dev/test)
        try {
            emailSender.send(contact.getEmail(), subject, html);
        } catch (RuntimeException ex) {
            // Email delivery failure must not break the token generation.
            // The raw URL is still returned in the response body for dev/test.
        }

        return new MagicLinkResponse("Magic link sent to " + email, magicLinkUrl);
    }

    // =========================================================================
    // Verify token
    // =========================================================================

    /**
     * Validates the magic-link token and returns a portal JWT.
     *
     * @param rawToken the raw token from the magic-link URL query param
     * @return portal JWT response (accessToken, 2 h TTL)
     * @throws PortalTokenInvalidException if the token is invalid, expired, or already used
     */
    public PortalTokenVerifyResponse verifyToken(String rawToken) {
        String tokenHash = sha256Hex(rawToken);

        PortalToken portalToken = portalTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new PortalTokenInvalidException("Invalid or expired token"));

        if (!portalToken.isValid()) {
            throw new PortalTokenInvalidException("Token has expired or already been used");
        }

        // Mark one-time use
        portalToken.markUsed();
        portalTokenRepository.save(portalToken);

        String jwt = portalJwtProvider.generate(
                portalToken.getContact().getId(),
                portalToken.getTenant().getId());

        return new PortalTokenVerifyResponse(jwt);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
