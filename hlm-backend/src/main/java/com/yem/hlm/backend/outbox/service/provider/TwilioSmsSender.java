package com.yem.hlm.backend.outbox.service.provider;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Twilio-backed {@link SmsSender} implementation.
 *
 * <p>Activated only when {@code app.sms.account-sid} is configured.
 * When active, it supersedes {@link NoopSmsSender} (which is {@code @ConditionalOnMissingBean}).
 * Twilio SDK is initialized lazily in the constructor.
 *
 * <h3>Configuration keys</h3>
 * <pre>
 * app.sms.account-sid  (env: TWILIO_ACCOUNT_SID)
 * app.sms.auth-token   (env: TWILIO_AUTH_TOKEN)
 * app.sms.from-number  (env: TWILIO_FROM)
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "app.sms.account-sid", matchIfMissing = false)
public class TwilioSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);

    private final String fromNumber;

    public TwilioSmsSender(
            @Value("${app.sms.account-sid}") String accountSid,
            @Value("${app.sms.auth-token}")  String authToken,
            @Value("${app.sms.from-number}") String fromNumber) {
        // Initialize Twilio SDK lazily in constructor
        Twilio.init(accountSid, authToken);
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(String to, String body) {
        try {
            Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(fromNumber),
                    body
            ).create();
            log.debug("[TWILIO] SMS sent to={}", to);
        } catch (ApiException e) {
            log.error("[TWILIO] Failed to send SMS to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Twilio send failed: " + e.getMessage(), e);
        }
    }
}
