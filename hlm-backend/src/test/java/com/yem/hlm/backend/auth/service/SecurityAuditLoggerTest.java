package com.yem.hlm.backend.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecurityAuditLogger — no Spring context required.
 */
class SecurityAuditLoggerTest {

    private SecurityAuditLogger logger;
    private ListAppender<ILoggingEvent> authAppender;
    private ListAppender<ILoggingEvent> societeAppender;
    private ListAppender<ILoggingEvent> rateLimitAppender;

    @BeforeEach
    void setUp() {
        logger = new SecurityAuditLogger();

        authAppender = attachAppender("security.auth");
        societeAppender = attachAppender("security.societe");
        rateLimitAppender = attachAppender("security.ratelimit");
    }

    @AfterEach
    void tearDown() {
        detachAppender("security.auth", authAppender);
        detachAppender("security.societe", societeAppender);
        detachAppender("security.ratelimit", rateLimitAppender);
    }

    private ListAppender<ILoggingEvent> attachAppender(String loggerName) {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(loggerName);
        logbackLogger.setLevel(ch.qos.logback.classic.Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        return appender;
    }

    private void detachAppender(String loggerName, ListAppender<ILoggingEvent> appender) {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(loggerName);
        logbackLogger.detachAppender(appender);
    }

    @Test
    void maskEmail_masksLocalPartBeyond3Chars() {
        assertThat(logger.maskEmail("johndoe@example.com")).isEqualTo("joh***@example.com");
    }

    @Test
    void maskEmail_shortLocalPart_masksWithoutTruncating() {
        assertThat(logger.maskEmail("ab@example.com")).isEqualTo("ab***@example.com");
    }

    @Test
    void maskEmail_nullEmail_returnsMask() {
        assertThat(logger.maskEmail(null)).isEqualTo("***");
    }

    @Test
    void maskEmail_noAtSign_returnsMask() {
        assertThat(logger.maskEmail("invalidemail")).isEqualTo("***");
    }

    @Test
    void logFailedLogin_containsCorrectEvent() {
        logger.logFailedLogin("acme", "johndoe@acme.com", "127.0.0.1", "BAD_CREDENTIALS");

        assertThat(authAppender.list).hasSize(1);
        String msg = authAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=FAILED_LOGIN");
        assertThat(msg).contains("identity=acme");
        assertThat(msg).contains("BAD_CREDENTIALS");
        // Email should be masked
        assertThat(msg).doesNotContain("johndoe@acme.com");
        assertThat(msg).contains("joh***@acme.com");
    }

    @Test
    void logSuccessfulLogin_containsLoginSuccess() {
        UUID userId = UUID.randomUUID();
        logger.logSuccessfulLogin("acme", userId, "10.0.0.1", "ROLE_ADMIN");

        assertThat(authAppender.list).hasSize(1);
        String msg = authAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=LOGIN_SUCCESS");
        assertThat(msg).contains("identity=acme");
        assertThat(msg).contains(userId.toString());
        assertThat(msg).contains("ROLE_ADMIN");
    }

    @Test
    void logTokenRevocation_containsCorrectEventAndReason() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        logger.logTokenRevocation("acme", targetId, actorId, "ROLE_CHANGE");

        assertThat(authAppender.list).hasSize(1);
        String msg = authAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=TOKEN_REVOCATION");
        assertThat(msg).contains("reason=ROLE_CHANGE");
        assertThat(msg).contains(targetId.toString());
    }

    @Test
    void logAccountLocked_containsLockedEvent() {
        UUID userId = UUID.randomUUID();
        Instant until = Instant.now().plusSeconds(900);
        logger.logAccountLocked("acme", userId, until, 5);

        assertThat(authAppender.list).hasSize(1);
        String msg = authAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=ACCOUNT_LOCKED");
        assertThat(msg).contains("attempts=5");
    }

    @Test
    void logCrossTenantAttempt_logsToTenantLogger() {
        UUID requested = UUID.randomUUID();
        UUID tokenSociete = UUID.randomUUID();
        logger.logCrossSocieteAttempt(requested, tokenSociete, "/api/properties");

        assertThat(societeAppender.list).hasSize(1);
        String msg = societeAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=CROSS_SOCIETE_ATTEMPT");
        assertThat(msg).contains("/api/properties");
    }

    @Test
    void logRateLimitTriggered_logsToRateLimitLogger() {
        logger.logRateLimitTriggered("10.1.2.3", "acme", "johndoe@acme.com", "IP");

        assertThat(rateLimitAppender.list).hasSize(1);
        String msg = rateLimitAppender.list.get(0).getFormattedMessage();
        assertThat(msg).contains("event=RATE_LIMIT_TRIGGERED");
        assertThat(msg).contains("bucketType=IP");
    }
}
