package com.yem.hlm.backend.outbox.service.provider;

/**
 * Strategy interface for sending outbound SMS messages.
 *
 * <p>Implementations can be swapped (Twilio, Vonage, AWS SNS …) without
 * touching the dispatcher or compose service.
 * The default implementation is {@link NoopSmsSender} (logs only).
 */
public interface SmsSender {

    /**
     * Sends an SMS.
     *
     * @param to   recipient phone number (E.164 format recommended)
     * @param body message text (≤160 chars for single segment)
     * @throws RuntimeException on provider error; the caller handles retries
     */
    void send(String to, String body);
}
