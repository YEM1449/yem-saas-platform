package com.yem.hlm.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured security audit logger.
 *
 * <p>Writes security events to named SLF4J loggers so that log aggregation
 * (e.g. ELK, Splunk) can route them independently of application logs.
 * All messages follow the format: {@code [SECURITY] event=X key=val ...}</p>
 *
 * <p>Loggers are named {@code security.auth}, {@code security.tenant},
 * and {@code security.ratelimit} — matching the production log-level overrides
 * in {@code application-production.yml}.</p>
 */
@Service
public class SecurityAuditLogger {

    private static final Logger authLog      = LoggerFactory.getLogger("security.auth");
    private static final Logger tenantLog    = LoggerFactory.getLogger("security.tenant");
    private static final Logger rateLimitLog = LoggerFactory.getLogger("security.ratelimit");

    /**
     * Logs a failed login attempt.
     *
     * @param tenantKey the tenant identifier from the request
     * @param email     the email address (will be masked)
     * @param ip        the client IP address
     * @param reason    BAD_CREDENTIALS | ACCOUNT_LOCKED | RATE_LIMITED | USER_NOT_FOUND
     */
    public void logFailedLogin(String tenantKey, String email, String ip, String reason) {
        authLog.warn("[SECURITY] event=FAILED_LOGIN tenant={} email={} ip={} reason={}",
                tenantKey, maskEmail(email), ip, reason);
    }

    /**
     * Logs when an account becomes locked after too many failed attempts.
     *
     * @param tenantKey   the tenant identifier
     * @param userId      the locked user's ID
     * @param lockedUntil the instant when the lock expires
     * @param attempts    the number of failed attempts that triggered lockout
     */
    public void logAccountLocked(String tenantKey, UUID userId, Instant lockedUntil, int attempts) {
        authLog.warn("[SECURITY] event=ACCOUNT_LOCKED tenant={} userId={} lockedUntil={} attempts={}",
                tenantKey, userId, lockedUntil, attempts);
    }

    /**
     * Logs a successful login.
     *
     * @param tenantKey the tenant identifier
     * @param userId    the authenticated user's ID
     * @param ip        the client IP address
     * @param role      the user's role (e.g. ROLE_ADMIN)
     */
    public void logSuccessfulLogin(String tenantKey, UUID userId, String ip, String role) {
        authLog.warn("[SECURITY] event=LOGIN_SUCCESS tenant={} userId={} ip={} role={}",
                tenantKey, userId, ip, role);
    }

    /**
     * Logs a token revocation event (role change or account disable).
     *
     * @param tenantKey      the tenant identifier
     * @param targetUserId   the user whose token is being revoked
     * @param actorUserId    the admin/manager performing the action
     * @param reason         ROLE_CHANGE | ACCOUNT_DISABLED
     */
    public void logTokenRevocation(String tenantKey, UUID targetUserId, UUID actorUserId, String reason) {
        authLog.warn("[SECURITY] event=TOKEN_REVOCATION tenant={} targetUserId={} actorUserId={} reason={}",
                tenantKey, targetUserId, actorUserId, reason);
    }

    /**
     * Logs a cross-tenant access attempt detected in the JWT filter.
     *
     * @param requestedTenantId the tenant ID from the request path/context
     * @param tokenTenantId     the tenant ID from the JWT claim
     * @param path              the request path
     */
    public void logCrossTenantAttempt(UUID requestedTenantId, UUID tokenTenantId, String path) {
        tenantLog.warn("[SECURITY] event=CROSS_TENANT_ATTEMPT requestedTenant={} tokenTenant={} path={}",
                requestedTenantId, tokenTenantId, path);
    }

    /**
     * Logs when a rate limit bucket is exhausted.
     *
     * @param ip         the client IP address
     * @param tenantKey  the tenant key from the request
     * @param email      the email address (will be masked)
     * @param bucketType IP | IDENTITY
     */
    public void logRateLimitTriggered(String ip, String tenantKey, String email, String bucketType) {
        rateLimitLog.warn("[SECURITY] event=RATE_LIMIT_TRIGGERED ip={} tenant={} email={} bucketType={}",
                ip, tenantKey, maskEmail(email), bucketType);
    }

    /**
     * Masks an email address for safe logging.
     * Example: "johndoe@acme.com" → "joh***@acme.com"
     */
    String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex); // includes the '@'
        if (local.length() <= 3) {
            return local + "***" + domain;
        }
        return local.substring(0, 3) + "***" + domain;
    }
}
