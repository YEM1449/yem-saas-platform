package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.api.dto.SocieteDto;
import com.yem.hlm.backend.auth.api.dto.SwitchSocieteRequest;
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

        // 2) Resolve user by email — unknown email = standard 401
        var userOpt = userRepository.findByEmail(email);
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
            // app_user_societe.role stores "ADMIN"/"MANAGER"/"AGENT"; JWT requires "ROLE_" prefix
            String role    = toJwtRole(membership.getRole());

            String token = jwtProvider.generate(user.getId(), societeId, role, user.getTokenVersion());
            long expiresInSeconds = jwtProperties.ttlSeconds();

            securityAuditLogger.logSuccessfulLogin(email, user.getId(), ip, role);
            log.debug("Login successful for email={} societe={} role={}", email, societeId, role);

            return LoginResponse.bearer(token, expiresInSeconds);
        } else {
            // Multiple memberships — build société list and ask the client to choose
            List<SocieteDto> societeDtos = memberships.stream()
                    .map(m -> {
                        Societe societe = societeRepository.findById(m.getSocieteId()).orElse(null);
                        String nom = societe != null ? societe.getNom() : m.getSocieteId().toString();
                        return new SocieteDto(m.getSocieteId(), nom);
                    })
                    .toList();

            log.debug("Login requires société selection for email={} memberships={}", email, memberships.size());
            return LoginResponse.selectSociete(societeDtos);
        }
    }

    /**
     * Issues a new JWT scoped to the requested société.
     * The caller must already hold a valid JWT (enforced by SecurityConfig).
     *
     * @param userId    authenticated user (from SecurityContextHolder principal)
     * @param req       the société to switch to
     * @return full JWT scoped to the requested société
     */
    @Transactional(readOnly = true)
    public LoginResponse switchSociete(UUID userId, SwitchSocieteRequest req) {
        UUID societeId = req.societeId();

        AppUserSociete membership = appUserSocieteRepository
                .findByIdUserIdAndIdSocieteId(userId, societeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "SOCIETE_NOT_IN_CLAIMS"));

        if (!membership.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SOCIETE_INACTIVE");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "NO_SOCIETE_ACCESS"));

        String role  = toJwtRole(membership.getRole());
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
