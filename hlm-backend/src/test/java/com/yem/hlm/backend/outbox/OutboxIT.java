package com.yem.hlm.backend.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.outbox.api.dto.SendMessageRequest;
import com.yem.hlm.backend.outbox.api.dto.SendMessageResponse;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.MessageStatus;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.outbox.service.OutboundDispatcherService;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the outbound message outbox (POST /api/messages, GET /api/messages).
 *
 * <p>Scheduler is disabled in the test profile; the dispatcher is invoked directly
 * to keep tests deterministic and independent of timing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutboxIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepo;
    @Autowired UserRepository userRepo;
    @Autowired OutboundMessageRepository messageRepo;
    @Autowired OutboundDispatcherService dispatcher;

    private String adminBearer;

    @BeforeEach
    void setUp() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. sendEmail_returnsPending_and202
    // =========================================================================

    @Test
    void sendEmail_returnsPending_and202() throws Exception {
        var req = emailRequest("outbox-it@example.com", "Hello", "Body text");

        String json = mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID msgId = om.readValue(json, SendMessageResponse.class).messageId();
        var msg = messageRepo.findByTenant_IdAndId(TENANT_ID, msgId).orElseThrow();

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.PENDING);
        assertThat(msg.getChannel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(msg.getRecipient()).isEqualTo("outbox-it@example.com");
        assertThat(msg.getSubject()).isEqualTo("Hello");
    }

    // =========================================================================
    // 2. sendSms_returnsPending_and202
    // =========================================================================

    @Test
    void sendSms_returnsPending_and202() throws Exception {
        var req = smsRequest("+212600000001", "Hi there");

        String json = mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID msgId = om.readValue(json, SendMessageResponse.class).messageId();
        var msg = messageRepo.findByTenant_IdAndId(TENANT_ID, msgId).orElseThrow();

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.PENDING);
        assertThat(msg.getChannel()).isEqualTo(MessageChannel.SMS);
        assertThat(msg.getRecipient()).isEqualTo("+212600000001");
    }

    // =========================================================================
    // 3. listMessages_returnsTenantMessages_only (isolation)
    // =========================================================================

    @Test
    void listMessages_tenantIsolation() throws Exception {
        // Arrange: create a PENDING message for the seed tenant
        mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(emailRequest("iso@example.com", "s", "b"))))
                .andExpect(status().isAccepted());

        // Create a second tenant and get a bearer for it
        Tenant t2 = tenantRepo.save(new Tenant("outbox-t2-key", "Outbox Tenant Two"));
        User u2 = new User(t2, "u2@outbox-t2.com", "hash");
        u2.setRole(UserRole.ROLE_ADMIN);
        u2 = userRepo.save(u2);
        String t2Bearer = "Bearer " + jwtProvider.generate(u2.getId(), t2.getId(), UserRole.ROLE_ADMIN);

        // Second tenant should see 0 messages
        mvc.perform(get("/api/messages")
                        .header("Authorization", t2Bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // First tenant should see exactly 1
        mvc.perform(get("/api/messages")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // =========================================================================
    // 4. sendEmail_withContactId_derivesRecipient
    // =========================================================================

    @Test
    void sendEmail_withContactId_derivesRecipientFromContact() throws Exception {
        ContactResponse contact = createContact("contact-mail@example.com", null);

        var req = new SendMessageRequest(
                MessageChannel.EMAIL, contact.id(), null, "From Contact", "Body", "CONTACT", contact.id());

        String json = mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        UUID msgId = om.readValue(json, SendMessageResponse.class).messageId();
        var msg = messageRepo.findByTenant_IdAndId(TENANT_ID, msgId).orElseThrow();

        assertThat(msg.getRecipient()).isEqualTo("contact-mail@example.com");
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.PENDING);
    }

    // =========================================================================
    // 5. sendMessage_withoutRecipientOrContact_returns400
    // =========================================================================

    @Test
    void sendMessage_withoutRecipientOrContact_returns400() throws Exception {
        var req = new SendMessageRequest(
                MessageChannel.EMAIL, null, null, "Subject", "Body", null, null);

        mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECIPIENT"));
    }

    // =========================================================================
    // 6. sendEmail_invalidRecipientFormat_returns400
    // =========================================================================

    @Test
    void sendEmail_invalidRecipientFormat_returns400() throws Exception {
        var req = emailRequest("not-an-email", "Subject", "Body");

        mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECIPIENT"));
    }

    // =========================================================================
    // 7. sendEmail_contactHasNoEmail_returns400
    // =========================================================================

    @Test
    void sendEmail_contactHasNoEmail_returns400() throws Exception {
        // Contact with phone only (no email)
        ContactResponse contact = createContact(null, "+212600000099");

        var req = new SendMessageRequest(
                MessageChannel.EMAIL, contact.id(), null, "Subject", "Body", null, null);

        mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTACT_CHANNEL_MISSING"));
    }

    // =========================================================================
    // 8. dispatcher_marksSentAfterRunDispatch (Noop sender always succeeds)
    // =========================================================================

    @Test
    void dispatcher_marksSentAfterRunDispatch() throws Exception {
        // Arrange: create a PENDING message
        String json = mvc.perform(post("/api/messages")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(emailRequest("dispatch@example.com", "s", "b"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        UUID msgId = om.readValue(json, SendMessageResponse.class).messageId();

        // Verify PENDING before dispatch
        assertThat(messageRepo.findByTenant_IdAndId(TENANT_ID, msgId).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.PENDING);

        // Act: trigger dispatcher directly (scheduler is disabled in test profile)
        dispatcher.runDispatch();

        // Assert: status changed to SENT
        var msg = messageRepo.findByTenant_IdAndId(TENANT_ID, msgId).orElseThrow();
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(msg.getSentAt()).isNotNull();
    }

    // =========================================================================
    // 9. sendMessage_withoutToken_returns401
    // =========================================================================

    @Test
    void sendMessage_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(emailRequest("r@x.com", "s", "b"))))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SendMessageRequest emailRequest(String to, String subject, String body) {
        return new SendMessageRequest(MessageChannel.EMAIL, null, to, subject, body, null, null);
    }

    private SendMessageRequest smsRequest(String to, String body) {
        return new SendMessageRequest(MessageChannel.SMS, null, to, null, body, null, null);
    }

    /** Creates a contact for the seed tenant via the API. */
    private ContactResponse createContact(String email, String phone) throws Exception {
        var req = new CreateContactRequest("John", "Outbox", phone, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readValue(json, ContactResponse.class);
    }
}
