package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.api.dto.SocieteDto;
import com.yem.hlm.backend.auth.api.dto.SwitchSocieteRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.auth.config.LockoutProperties;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserSocieteRepository appUserSocieteRepository;
    private final SocieteRepository societeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final LockoutProperties lockoutProperties;
    private final UserSecurityCacheService userSecurityCacheService;
    private final SecurityAuditLogger securityAuditLogger;

    public AuthService(AppUserSocieteRepository appUserSocieteRepository,
                       SocieteRepository societeRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties,
                       LoginRateLimiter loginRateLimiter,
                       LockoutProperties lockoutProperties,
                       UserSecurityCacheService userSecurityCacheService,
                       SecurityAuditLogger securityAuditLogger) {
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.societeRepository         = societeRepository;
        this.userRepository            = userRepository;
        this.passwordEncoder           = passwordEncoder;
        this.jwtProvider               = jwtProvider;
        this.jwtProperties             = jwtProperties;
        this.loginRateLimiter          = loginRateLimiter;
        this.lockoutProperties         = lockoutProperties;
        this.userSecurityCacheService  = userSecurityCacheService;
        this.securityAuditLogger       = securityAuditLogger;
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest req) {
        String email    = req.email().trim().toLowerCase();
        String password = req.password();

        // 1) Rate-limit check (IP + email identity) — before any DB access
        String ip = extractClientIp();
        LoginRateLimiter.RateLimitResult rlResult = loginRateLimiter.tryConsume(ip, email, email);
        if (!rlResult.allowed()) {
            securityAuditLogger.logRateLimitTriggered(ip, email, email, "IP_OR_IDENTITY");
            securityAuditLogger.logFailedLogin(email, email, ip, "RATE_LIMITED");
            throw new LoginRateLimitedException(rlResult.waitSeconds());
        }

        // 2) Resolve user by email — unknown email = standard 401.
        // findFirstByEmail is used instead of findByEmail to safely handle pre-migration
        // deployments that may have duplicate email rows (changeset 036 removes them).
        var userOpt = userRepository.findFirstByEmail(email);
        if (userOpt.isEmpty()) {
            securityAuditLogger.logFailedLogin(email, email, ip, "USER_NOT_FOUND");
            throw new UnauthorizedException();
        }
        var user = userOpt.get();

        // 3) Account lockout check
        if (user.isLockedOut()) {
            securityAuditLogger.logFailedLogin(email, email, ip, "ACCOUNT_LOCKED");
            throw new AccountLockedException(user.getLockedUntil());
        }

        // 4) Account disabled check
        if (!user.isEnabled()) {
            securityAuditLogger.logFailedLogin(email, email, ip, "BAD_CREDENTIALS");
            throw new UnauthorizedException();
        }

        // 5) Password verification
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedAttempt(lockoutProperties.getMaxAttempts(), lockoutProperties.getDurationMinutes());
            userRepository.save(user);
            userSecurityCacheService.evict(user.getId());
            securityAuditLogger.logFailedLogin(email, email, ip, "BAD_CREDENTIALS");
            if (user.isLockedOut()) {
                securityAuditLogger.logAccountLocked(email, user.getId(), user.getLockedUntil(), user.getFailedLoginAttempts());
            }
            throw new UnauthorizedException();
        }

        // 6) Resolve société memberships
        List<AppUserSociete> memberships = appUserSocieteRepository.findByIdUserIdAndActifTrue(user.getId());

        // SUPER_ADMIN with no société memberships is a valid platform operator —
        // mint a platform-level JWT (no 'sid' claim) that bypasses société context.
        if ("SUPER_ADMIN".equals(user.getPlatformRole()) && memberships.isEmpty()) {
            String token = jwtProvider.generate(user.getId(), null, "ROLE_SUPER_ADMIN", user.getTokenVersion());
            securityAuditLogger.logSuccessfulLogin(email, user.getId(), ip, "ROLE_SUPER_ADMIN");
            log.debug("Login successful for SUPER_ADMIN email={} (no société membership)", email);
            return LoginResponse.bearer(token, jwtProperties.ttlSeconds());
        }

        if (memberships.isEmpty()) {
            securityAuditLogger.logFailedLogin(email, email, ip, "NO_SOCIETE_MEMBERSHIP");
            throw new UnauthorizedException();
        }

        // 7) Successful login — reset any previous failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.resetLoginAttempts();
            userRepository.save(user);
        }

        // 8) If exactly one membership, auto-select and return full JWT.
        //    If multiple memberships, prompt the client to select a société.
        if (memberships.size() == 1) {
            AppUserSociete membership = memberships.get(0);
            UUID societeId = membership.getSocieteId();
            // platform_role takes precedence: SUPER_ADMIN at platform level overrides société role
            String role = "SUPER_ADMIN".equals(user.getPlatformRole())
                    ? "ROLE_SUPER_ADMIN"
                    : toJwtRole(membership.getRole());

            String token = jwtProvider.generate(user.getId(), societeId, role, user.getTokenVersion());
            long expiresInSeconds = jwtProperties.ttlSeconds();

            securityAuditLogger.logSuccessfulLogin(email, user.getId(), ip, role);
            log.debug("Login successful for email={} societe={} role={}", email, societeId, role);

            return LoginResponse.bearer(token, expiresInSeconds);
        } else {
            // Multiple memberships — build société list and ask the client to choose.
            // Mint a short-lived partial token so the client can call /auth/switch-societe.
            List<SocieteDto> societeDtos = memberships.stream()
                    .map(m -> {
                        Societe societe = societeRepository.findById(m.getSocieteId()).orElse(null);
                        String nom = societe != null ? societe.getNom() : m.getSocieteId().toString();
                        return new SocieteDto(m.getSocieteId(), nom);
                    })
                    .toList();

            String partialToken = jwtProvider.generatePartial(user.getId(), 300);
            log.debug("Login requires société selection for email={} memberships={}", email, memberships.size());
            return LoginResponse.selectSociete(partialToken, societeDtos);
        }
    }

    /**
     * Issues a new JWT scoped to the requested société.
     * The caller presents either a partial token (issued during multi-société selection)
     * or a full token (re-selecting a société). Token validation is performed here
     * because /auth/switch-societe is permitAll in SecurityConfig.
     *
     * @param authorizationHeader  raw "Authorization: Bearer <token>" header (may be null)
     * @param req                  the société to switch to
     * @return full JWT scoped to the requested société
     */
    @Transactional(readOnly = true)
    public LoginResponse switchSociete(String authorizationHeader, SwitchSocieteRequest req) {
        // 1. Extract and validate the token
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_OR_MISSING_TOKEN");
        }
        String rawToken = authorizationHeader.substring(7);

        Jwt jwt;
        try {
            jwt = jwtProvider.parse(rawToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_OR_MISSING_TOKEN");
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_OR_MISSING_TOKEN");
        }

        UUID societeId = req.societeId();

        // 2. Verify the user has an active membership in the requested société
        AppUserSociete membership = appUserSocieteRepository
                .findByIdUserIdAndIdSocieteId(userId, societeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "SOCIETE_NOT_IN_MEMBERSHIPS"));

        if (!membership.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SOCIETE_INACTIVE");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "NO_SOCIETE_ACCESS"));

        // 3. Mint a full scoped JWT (platformRole takes precedence — see Fix 4)
        String role = "SUPER_ADMIN".equals(user.getPlatformRole())
                ? "ROLE_SUPER_ADMIN"
                : toJwtRole(membership.getRole());
        String token = jwtProvider.generate(userId, societeId, role, user.getTokenVersion());
        long expiresInSeconds = jwtProperties.ttlSeconds();

        log.debug("switchSociete userId={} societeId={} role={}", userId, societeId, role);
        return LoginResponse.bearer(token, expiresInSeconds);
    }

    /**
     * Returns the client IP as reported by the servlet container.
     * When {@code server.forward-headers-strategy=native} is set, Tomcat's RemoteIpValve
     * rewrites remoteAddr from X-Forwarded-For only for requests arriving from trusted
     * private-network proxies, preventing attacker-controlled header spoofing.
     */
    /**
     * Ensures the role stored in app_user_societe ("ADMIN","MANAGER","AGENT")
     * is converted to the "ROLE_" prefixed form required by Spring Security / JWT claims.
     * If the value already has the prefix (e.g. from an old migration), it is returned as-is.
     */
    private static String toJwtRole(String role) {
        if (role == null) return "ROLE_AGENT";
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
