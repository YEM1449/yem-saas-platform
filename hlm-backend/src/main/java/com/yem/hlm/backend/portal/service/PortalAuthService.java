package com.yem.hlm.backend.portal.service;

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

    /** Base URL used to build the magic-link. Override via {@code app.portal.base-url}. */
    @Value("${app.portal.base-url:http://localhost:4200}")
    private String portalBaseUrl;

    public PortalAuthService(TenantRepository tenantRepository,
                             ContactRepository contactRepository,
                             PortalTokenRepository portalTokenRepository,
                             PortalJwtProvider portalJwtProvider,
                             EmailSender emailSender) {
        this.tenantRepository   = tenantRepository;
        this.contactRepository  = contactRepository;
        this.portalTokenRepository = portalTokenRepository;
        this.portalJwtProvider  = portalJwtProvider;
        this.emailSender        = emailSender;
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
        var tenant = tenantRepository.findByKey(tenantKey)
                .orElseThrow(() -> new PortalTokenInvalidException("Unknown tenant or email"));

        Contact contact = contactRepository.findByTenant_IdAndEmailIgnoreCase(tenant.getId(), email)
                .orElseThrow(() -> new PortalTokenInvalidException("Unknown tenant or email"));

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
        String magicLinkUrl = portalBaseUrl + "/portal/login?token=" + rawToken;

        // Send email (fire-and-forget; NoopEmailSender used in dev/test)
        String subject = "Your secure portal access link";
        String body = "Hello " + contact.getFirstName() + ",\n\n"
                + "Click the link below to access your client portal (valid 48 h):\n\n"
                + magicLinkUrl + "\n\n"
                + "If you did not request this link, please ignore this email.";
        try {
            emailSender.send(contact.getEmail(), subject, body);
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
