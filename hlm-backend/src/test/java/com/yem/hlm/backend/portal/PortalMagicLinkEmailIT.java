package com.yem.hlm.backend.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.UserRole;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying that POST /api/portal/auth/request-link
 * actually dispatches an HTML magic-link email when a real SMTP sender is active.
 *
 * GreenMail acts as an in-process SMTP server on port 3025.
 * The SmtpEmailSender bean is activated via app.email.host=localhost.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.email.host=localhost",
        "app.email.port=3025",
        "app.email.username=",
        "app.email.password=",
        "app.email.from=noreply@hlm.local",
        "app.portal.base-url=http://localhost:4200",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false"
})
class PortalMagicLinkEmailIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static GreenMail greenMail;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;

    private String adminBearer;

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

    @BeforeEach
    void setup() throws Exception {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);

        // Create the contact that will request a magic link
        var req = new CreateContactRequest("Portal", "Tester", null, "portal-test@example.com",
                null, null, null, null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void requestLink_sends_html_magic_link_email() throws Exception {
        greenMail.reset();

        mvc.perform(post("/api/portal/auth/request-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"portal-test@example.com\",\"tenantKey\":\"acme\"}"))
                .andExpect(status().isOk());

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage message = received[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("portal-test@example.com");
        assertThat(message.getSubject()).containsIgnoringCase("portail");

        // Extract body — may be plain String or Multipart
        String body;
        Object content = message.getContent();
        if (content instanceof jakarta.mail.Multipart multipart) {
            body = multipart.getBodyPart(0).getContent().toString();
        } else {
            body = content.toString();
        }

        assertThat(body).containsIgnoringCase("portal/verify?token=");
        assertThat(body).containsIgnoringCase("<html");
    }

    @Test
    void requestLink_unknown_email_returns_200_without_sending_email() throws Exception {
        greenMail.reset();

        // Unknown email — server should return 200 (no enumeration) and not send any mail
        mvc.perform(post("/api/portal/auth/request-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@unknown.com\",\"tenantKey\":\"acme\"}"))
                .andExpect(status().isOk());

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
