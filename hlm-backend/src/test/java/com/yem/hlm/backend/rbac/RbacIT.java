package com.yem.hlm.backend.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC integration tests for ContactController, DepositController, and NotificationController.
 * Asserts 401 (no token), 403 (wrong role), 200/201 (allowed role).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RbacIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;

    @BeforeEach
    void setup() {
        String key = "rbac-" + UUID.randomUUID().toString().substring(0, 8);
        Societe societe = societeRepository.save(new Societe("Acme Corp", "MA"));

        User admin = new User("admin@rbac.test", "hash");
        admin = userRepository.save(admin);

        User manager = new User("mgr@rbac.test", "hash");
        manager = userRepository.save(manager);

        User agent = new User("agent@rbac.test", "hash");
        agent = userRepository.save(agent);

        adminBearer = "Bearer " + jwtProvider.generate(admin.getId(), societe.getId(), UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(manager.getId(), societe.getId(), UserRole.ROLE_MANAGER);
        agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), societe.getId(), UserRole.ROLE_AGENT);
    }

    // ===== ContactController: POST /api/contacts =====

    @Test
    void createContact_noToken_returns401() throws Exception {
        mvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createContact_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/contacts")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createContact_asManager_returns201() throws Exception {
        mvc.perform(post("/api/contacts")
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void createContact_asAdmin_returns201() throws Exception {
        mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactJson()))
                .andExpect(status().isCreated());
    }

    // ===== ContactController: GET /api/contacts =====

    @Test
    void listContacts_noToken_returns401() throws Exception {
        mvc.perform(get("/api/contacts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listContacts_asAgent_returns200() throws Exception {
        mvc.perform(get("/api/contacts")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk());
    }

    // ===== ContactController: PATCH /api/contacts/{id} =====

    @Test
    void updateContact_asAgent_returns403() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(patch("/api/contacts/{id}", contactId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\",\"lastName\":\"Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateContact_asManager_returns200() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(patch("/api/contacts/{id}", contactId)
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\",\"lastName\":\"Name\"}"))
                .andExpect(status().isOk());
    }

    // ===== ContactController: PATCH /api/contacts/{id}/status =====

    @Test
    void updateContactStatus_asAgent_returns403() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateContactStatus_asManager_returns200() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isOk());
    }

    // ===== ContactController: POST /api/contacts/{id}/interests =====

    @Test
    void addInterest_asAgent_returns403() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(post("/api/contacts/{id}/interests", contactId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== ContactController: DELETE /api/contacts/{id}/interests/{propertyId} =====

    @Test
    void removeInterest_asManager_returns403() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(delete("/api/contacts/{id}/interests/{propertyId}", contactId, UUID.randomUUID())
                        .header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeInterest_asAgent_returns403() throws Exception {
        String contactId = createContactAs(adminBearer);
        mvc.perform(delete("/api/contacts/{id}/interests/{propertyId}", contactId, UUID.randomUUID())
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // ===== DepositController: POST /api/deposits =====

    @Test
    void createDeposit_noToken_returns401() throws Exception {
        mvc.perform(post("/api/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDeposit_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/deposits")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":\"" + UUID.randomUUID() + "\",\"propertyId\":\"" + UUID.randomUUID() + "\",\"amount\":1000}"))
                .andExpect(status().isForbidden());
    }

    // ===== DepositController: POST /api/deposits/{id}/confirm =====

    @Test
    void confirmDeposit_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/deposits/{id}/confirm", UUID.randomUUID())
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // ===== DepositController: POST /api/deposits/{id}/cancel =====

    @Test
    void cancelDeposit_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/deposits/{id}/cancel", UUID.randomUUID())
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // ===== DepositController: GET /api/deposits/{id} =====

    @Test
    void getDeposit_asAgent_returns401WithoutToken() throws Exception {
        mvc.perform(get("/api/deposits/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== DepositController: GET /api/deposits/report =====

    @Test
    void depositReport_asAgent_returns403() throws Exception {
        mvc.perform(get("/api/deposits/report")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositReport_asManager_returns200() throws Exception {
        mvc.perform(get("/api/deposits/report")
                        .header("Authorization", managerBearer))
                .andExpect(status().isOk());
    }

    // ===== NotificationController: GET /api/notifications =====

    @Test
    void listNotifications_noToken_returns401() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listNotifications_asAgent_returns200() throws Exception {
        mvc.perform(get("/api/notifications")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk());
    }

    // ===== NotificationController: POST /api/notifications/{id}/read =====

    @Test
    void markNotificationRead_noToken_returns401() throws Exception {
        mvc.perform(post("/api/notifications/{id}/read", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== Helpers =====

    private String contactJson() {
        return "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane" + UUID.randomUUID().toString().substring(0, 6) + "@test.com\"}";
    }

    private String createContactAs(String bearer) throws Exception {
        String body = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }
}
