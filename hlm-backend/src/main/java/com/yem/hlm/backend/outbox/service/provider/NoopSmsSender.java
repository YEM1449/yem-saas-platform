package com.yem.hlm.backend.outbox.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * No-op SMS sender — active when no real {@link SmsSender} bean is configured.
 *
 * <p>Simulates a successful send by logging the message. Replace with a real
 * provider implementation (e.g. Twilio, Vonage) for production.
 */
@Service
@ConditionalOnMissingBean(value = SmsSender.class, ignored = NoopSmsSender.class)
public class NoopSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(NoopSmsSender.class);

    @Override
    public void send(String to, String body) {
        log.info("[NOOP-SMS] to={} bodyLength={}", to, body != null ? body.length() : 0);
    }
}
