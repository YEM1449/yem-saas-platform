package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.auth.config.LockoutProperties;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final LockoutProperties lockoutProperties;
    private final UserSecurityCacheService userSecurityCacheService;
    private final SecurityAuditLogger securityAuditLogger;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties,
                       LoginRateLimiter loginRateLimiter,
                       LockoutProperties lockoutProperties,
                       UserSecurityCacheService userSecurityCacheService,
                       SecurityAuditLogger securityAuditLogger) {
        this.tenantRepository         = tenantRepository;
        this.userRepository           = userRepository;
        this.passwordEncoder          = passwordEncoder;
        this.jwtProvider              = jwtProvider;
        this.jwtProperties            = jwtProperties;
        this.loginRateLimiter         = loginRateLimiter;
        this.lockoutProperties        = lockoutProperties;
        this.userSecurityCacheService = userSecurityCacheService;
        this.securityAuditLogger      = securityAuditLogger;
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest req) {
        String tenantKey = req.tenantKey().trim().toLowerCase();
        String email     = req.email().trim().toLowerCase();
        String password  = req.password();

        // 1) Rate-limit check (IP + identity) — before any DB access
        String ip = extractClientIp();
        LoginRateLimiter.RateLimitResult rlResult = loginRateLimiter.tryConsume(ip, tenantKey, email);
        if (!rlResult.allowed()) {
            securityAuditLogger.logRateLimitTriggered(ip, tenantKey, email, "IP_OR_IDENTITY");
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "RATE_LIMITED");
            throw new LoginRateLimitedException(rlResult.waitSeconds());
        }

        // 2) Resolve tenant — unknown tenant = standard 401
        var tenantOpt = tenantRepository.findByKey(tenantKey);
        if (tenantOpt.isEmpty()) {
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "USER_NOT_FOUND");
            throw new UnauthorizedException();
        }
        var tenant = tenantOpt.get();

        // 3) Resolve user — unknown email = standard 401
        var userOpt = userRepository.findByTenant_IdAndEmail(tenant.getId(), email);
        if (userOpt.isEmpty()) {
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "USER_NOT_FOUND");
            throw new UnauthorizedException();
        }
        var user = userOpt.get();

        // 4) Account lockout check
        if (user.isLockedOut()) {
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "ACCOUNT_LOCKED");
            throw new AccountLockedException(user.getLockedUntil());
        }

        // 5) Account disabled check
        if (!user.isEnabled()) {
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "BAD_CREDENTIALS");
            throw new UnauthorizedException();
        }

        // 6) Password verification
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedAttempt(lockoutProperties.getMaxAttempts(), lockoutProperties.getDurationMinutes());
            userRepository.save(user);
            userSecurityCacheService.evict(user.getId());
            securityAuditLogger.logFailedLogin(tenantKey, email, ip, "BAD_CREDENTIALS");
            if (user.isLockedOut()) {
                securityAuditLogger.logAccountLocked(tenantKey, user.getId(), user.getLockedUntil(), user.getFailedLoginAttempts());
            }
            throw new UnauthorizedException();
        }

        // 7) Successful login — reset any previous failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.resetLoginAttempts();
            userRepository.save(user);
        }

        String token = jwtProvider.generate(user.getId(), tenant.getId(), user.getRole(), user.getTokenVersion());
        long expiresInSeconds = jwtProperties.ttlSeconds();

        securityAuditLogger.logSuccessfulLogin(tenantKey, user.getId(), ip, user.getRole().name());
        log.debug("Login successful for tenant={} user={} role={}", tenantKey, email, user.getRole());

        return LoginResponse.bearer(token, expiresInSeconds);
    }

    /**
     * Returns the client IP as reported by the servlet container.
     * When {@code server.forward-headers-strategy=native} is set, Tomcat's RemoteIpValve
     * rewrites remoteAddr from X-Forwarded-For only for requests arriving from trusted
     * private-network proxies, preventing attacker-controlled header spoofing.
     */
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
