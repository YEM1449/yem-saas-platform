package com.yem.hlm.backend.outbox.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * No-op email sender — active when no real {@link EmailSender} bean is configured.
 *
 * <p>Simulates a successful send by logging the message. Replace with a real
 * SMTP or API-based implementation (e.g. {@code SmtpEmailSender}) for production.
 */
@Service
@ConditionalOnMissingBean(value = EmailSender.class, ignored = NoopEmailSender.class)
public class NoopEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[NOOP-EMAIL] to={} subject=\"{}\" bodyLength={}",
                to, subject, body != null ? body.length() : 0);
    }
}
