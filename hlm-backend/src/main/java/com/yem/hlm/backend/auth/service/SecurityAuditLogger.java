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
 * <p>Loggers are named {@code security.auth}, {@code security.societe},
 * and {@code security.ratelimit} — matching the production log-level overrides
 * in {@code application-production.yml}.</p>
 */
@Service
public class SecurityAuditLogger {

    private static final Logger authLog      = LoggerFactory.getLogger("security.auth");
    private static final Logger societeLog   = LoggerFactory.getLogger("security.societe");
    private static final Logger rateLimitLog = LoggerFactory.getLogger("security.ratelimit");

    /**
     * Logs a failed login attempt.
     *
     * @param identity  identity string logged with the event (e.g. email)
     * @param email     the email address (will be masked)
     * @param ip        the client IP address
     * @param reason    BAD_CREDENTIALS | ACCOUNT_LOCKED | RATE_LIMITED | USER_NOT_FOUND
     */
    public void logFailedLogin(String identity, String email, String ip, String reason) {
        authLog.warn("[SECURITY] event=FAILED_LOGIN identity={} email={} ip={} reason={}",
                identity, maskEmail(email), ip, reason);
    }

    /**
     * Logs when an account becomes locked after too many failed attempts.
     *
     * @param identity    identity string logged with the event (e.g. email)
     * @param userId      the locked user's ID
     * @param lockedUntil the instant when the lock expires
     * @param attempts    the number of failed attempts that triggered lockout
     */
    public void logAccountLocked(String identity, UUID userId, Instant lockedUntil, int attempts) {
        authLog.warn("[SECURITY] event=ACCOUNT_LOCKED identity={} userId={} lockedUntil={} attempts={}",
                identity, userId, lockedUntil, attempts);
    }

    /**
     * Logs a successful login.
     *
     * @param identity  identity string logged with the event (e.g. email)
     * @param userId    the authenticated user's ID
     * @param ip        the client IP address
     * @param role      the user's role (e.g. ROLE_ADMIN)
     */
    public void logSuccessfulLogin(String identity, UUID userId, String ip, String role) {
        authLog.warn("[SECURITY] event=LOGIN_SUCCESS identity={} userId={} ip={} role={}",
                identity, userId, ip, role);
    }

    /**
     * Logs a token revocation event (role change or account disable).
     *
     * @param identity       identity string logged with the event (e.g. email)
     * @param targetUserId   the user whose token is being revoked
     * @param actorUserId    the admin/manager performing the action
     * @param reason         ROLE_CHANGE | ACCOUNT_DISABLED
     */
    public void logTokenRevocation(String identity, UUID targetUserId, UUID actorUserId, String reason) {
        authLog.warn("[SECURITY] event=TOKEN_REVOCATION identity={} targetUserId={} actorUserId={} reason={}",
                identity, targetUserId, actorUserId, reason);
    }

    /**
     * Logs a cross-tenant access attempt detected in the JWT filter.
     *
     * @param requestedTenantId the tenant ID from the request path/context
     * @param tokenTenantId     the tenant ID from the JWT claim
     * @param path              the request path
     */
    public void logCrossSocieteAttempt(UUID requestedSocieteId, UUID tokenSocieteId, String path) {
        societeLog.warn("[SECURITY] event=CROSS_SOCIETE_ATTEMPT requestedSociete={} tokenSociete={} path={}",
                requestedSocieteId, tokenSocieteId, path);
    }

    /**
     * Logs when a rate limit bucket is exhausted.
     *
     * @param ip         the client IP address
     * @param identity   identity string logged with the event (e.g. email)
     * @param email      the email address (will be masked)
     * @param bucketType IP | IDENTITY
     */
    public void logRateLimitTriggered(String ip, String identity, String email, String bucketType) {
        rateLimitLog.warn("[SECURITY] event=RATE_LIMIT_TRIGGERED ip={} identity={} email={} bucketType={}",
                ip, identity, maskEmail(email), bucketType);
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
