package com.yem.hlm.backend.outbox.service.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Brevo (ex-Sendinblue) transactional HTTP API email sender.
 *
 * <p>Activated when {@code app.email.provider=brevo-http} and {@code app.email.brevo.api-key}
 * is configured. Uses HTTPS (port 443) which is rarely blocked by outbound firewalls —
 * this is the recommended provider when SMTP ports (25/465/587) are filtered by the host.
 *
 * <h3>Configuration keys</h3>
 * <pre>
 * app.email.provider       = brevo-http   (env: EMAIL_PROVIDER)
 * app.email.brevo.api-key  (env: BREVO_API_KEY)       — required
 * app.email.brevo.base-url (env: BREVO_BASE_URL)      — default https://api.brevo.com
 * app.email.from           (env: EMAIL_FROM)          — sender address
 * app.email.from-name      (env: EMAIL_FROM_NAME)     — optional sender display name
 * </pre>
 *
 * @see <a href="https://developers.brevo.com/reference/sendtransacemail">Brevo docs</a>
 */
@Service
@ConditionalOnExpression(
    "'${app.email.provider:}'.equalsIgnoreCase('brevo-http') && !'${app.email.brevo.api-key:}'.isBlank()"
)
public class BrevoHttpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoHttpEmailSender.class);
    private static final java.util.regex.Pattern HTML_TAG_PATTERN =
            java.util.regex.Pattern.compile("<[a-zA-Z][^>]*>", java.util.regex.Pattern.DOTALL);

    private final RestClient client;
    private final String     from;
    private final String     fromName;

    public BrevoHttpEmailSender(
            @Value("${app.email.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${app.email.brevo.api-key}") String apiKey,
            @Value("${app.email.from}") String from,
            @Value("${app.email.from-name:}") String fromName) {
        this.from     = from;
        this.fromName = fromName;
        this.client   = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[BREVO-HTTP] Email sender initialised — baseUrl={} from={}", baseUrl, from);
    }

    @Override
    public void send(String to, String subject, String body) {
        Map<String, Object> sender = new HashMap<>();
        sender.put("email", from);
        if (fromName != null && !fromName.isBlank()) {
            sender.put("name", fromName);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender",  sender);
        payload.put("to",      List.of(Map.of("email", to)));
        payload.put("subject", subject != null ? subject : "");

        boolean isHtml = body != null && HTML_TAG_PATTERN.matcher(body).find();
        if (isHtml) {
            payload.put("htmlContent", body);
        } else {
            payload.put("textContent", body != null ? body : "");
        }

        try {
            client.post()
                  .uri("/v3/smtp/email")
                  .body(payload)
                  .retrieve()
                  .toBodilessEntity();
            log.debug("[BREVO-HTTP] Sent email to={} subject=\"{}\"", to, subject);
        } catch (RestClientResponseException e) {
            log.error("[BREVO-HTTP] Failed to send email to={} status={} body={}",
                    to, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException(
                "Brevo HTTP send failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[BREVO-HTTP] Failed to send email to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Brevo HTTP send failed: " + e.getMessage(), e);
        }
    }
}
