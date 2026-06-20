package com.yem.hlm.backend.outbox.health;

import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.outbox.service.provider.NoopEmailSender;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces the email dependency on {@code /actuator/health} (P4 / DA-026 — "health doesn't reflect
 * external deps").
 *
 * <p>It does <b>not</b> probe Brevo/SMTP on every poll (that would burn API quota and is the wrong
 * cadence). Instead it reports which {@link EmailSender} is wired and whether it actually delivers:
 * a {@link NoopEmailSender} silently drops every message, so a deploy that forgot to configure a real
 * provider would otherwise "succeed" while no transactional email is ever sent. Status stays UP (a
 * mail-config issue must not fail readiness and de-route the container) but {@code delivering=false}
 * makes the misconfiguration visible to monitoring/alerting. Actual send failures show up as ERROR
 * logs and the {@code hlm.errors.unhandled} counter.
 */
@Component
public class EmailProviderHealthIndicator implements HealthIndicator {

    private final EmailSender emailSender;

    public EmailProviderHealthIndicator(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public Health health() {
        boolean delivering = !(emailSender instanceof NoopEmailSender);
        return Health.up()
                .withDetail("provider", emailSender.getClass().getSimpleName())
                .withDetail("delivering", delivering)
                .build();
    }
}
