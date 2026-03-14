package com.yem.hlm.backend.outbox;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.yem.hlm.backend.outbox.service.provider.SmtpEmailSender;
import com.yem.hlm.backend.support.IntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link SmtpEmailSender}.
 *
 * <p>GreenMail acts as an in-process SMTP server on port 3025.
 * The SmtpEmailSender bean is activated by setting app.email.host=localhost.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.email.host=localhost",
        "app.email.port=3025",
        "app.email.username=",
        "app.email.password=",
        "app.email.from=test@hlm.local",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false"
})
class SmtpEmailSenderIT extends IntegrationTestBase {

    private static GreenMail greenMail;

    @Autowired
    private SmtpEmailSender smtpEmailSender;

    @BeforeAll
    static void startGreenMail() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void send_delivers_message_to_greenmail() throws Exception {
        greenMail.reset();

        smtpEmailSender.send(
                "recipient@example.com",
                "Test Subject",
                "<html><body>Hello from test</body></html>"
        );

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
        assertThat(received[0].getSubject()).isEqualTo("Test Subject");
    }

    @Test
    void send_with_html_body_is_detected_as_html() throws Exception {
        greenMail.reset();

        smtpEmailSender.send(
                "buyer@example.com",
                "Portal Access",
                "<html><body><h1>Welcome</h1></body></html>"
        );

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getContentType()).containsIgnoringCase("text/html");
    }
}
