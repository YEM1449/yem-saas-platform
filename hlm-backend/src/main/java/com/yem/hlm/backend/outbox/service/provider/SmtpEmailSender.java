package com.yem.hlm.backend.outbox.service.provider;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * SMTP-backed {@link EmailSender} implementation.
 *
 * <p>Activated only when {@code app.email.host} is configured in the environment.
 * When active, it supersedes {@link NoopEmailSender} (which is {@code @ConditionalOnMissingBean}).
 *
 * <h3>Configuration keys (all overridable via env vars)</h3>
 * <pre>
 * app.email.host     (env: EMAIL_HOST)
 * app.email.port     (env: EMAIL_PORT,     default 587)
 * app.email.username (env: EMAIL_USER)
 * app.email.password (env: EMAIL_PASSWORD)
 * app.email.from     (env: EMAIL_FROM)
 * </pre>
 *
 * <p>Spring Boot auto-configures {@link JavaMailSender} from {@code spring.mail.*} properties.
 * The bridge between {@code app.email.*} and {@code spring.mail.*} is provided in
 * {@code application.yml}.
 */
@Service
@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    /** Matches any opening HTML tag, e.g. {@code <b>}, {@code <p class="x">}, {@code <html>}. */
    private static final java.util.regex.Pattern HTML_TAG_PATTERN =
            java.util.regex.Pattern.compile("<[a-zA-Z][^>]*>", java.util.regex.Pattern.DOTALL);

    private final JavaMailSender mailSender;
    private final String         from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${app.email.from}") String from) {
        this.mailSender = mailSender;
        this.from       = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject != null ? subject : "");
            // Send as HTML when the body contains ANY HTML tag (not just a full <html> document).
            // The original check `body.contains("<html")` missed bodies with partial HTML
            // such as "<b>bold</b>" or "<p>paragraph</p>", which were sent as plain text
            // and rendered as raw markup in email clients.
            boolean isHtml = body != null && HTML_TAG_PATTERN.matcher(body).find();
            helper.setText(body != null ? body : "", isHtml);
            mailSender.send(message);
            log.debug("[SMTP] Sent email to={} subject=\"{}\"", to, subject);
        } catch (Exception e) {
            log.error("[SMTP] Failed to send email to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
