package com.yem.hlm.backend.outbox.service.provider;

/**
 * Strategy interface for sending outbound email messages.
 *
 * <p>Implementations can be swapped (SMTP, SendGrid, SES …) without
 * touching the dispatcher or compose service.
 * The default implementation is {@link NoopEmailSender} (logs only).
 */
public interface EmailSender {

    /**
     * Sends an email.
     *
     * @param to      recipient email address
     * @param subject email subject (may be blank but not null)
     * @param body    plain-text or HTML body
     * @throws RuntimeException on provider error; the caller handles retries
     */
    void send(String to, String subject, String body);
}
