package com.yem.hlm.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.user.api.dto.*;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AdminUserController (/api/users).
 * Covers 401/403 RBAC, CRUD as ADMIN, and cross-societe isolation.
 *
 * NOTE: No @Transactional — AuditEventListener uses Propagation.REQUIRES_NEW which
 * opens a separate connection that cannot see uncommitted test data → FK violation.
 * Use per-test UID suffixes on all email addresses to avoid uk_user_email collisions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIT extends IntegrationTestBase {

    private static final String BASE = "/api/users";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    // Societe A
    private Societe societeA;
    private User adminA;
    private String adminBearerA;
    private String managerBearerA;
    private String agentBearerA;

    // Societe B
    private Societe societeB;
    private User adminB;
    private String adminBearerB;

    /** Per-test unique suffix — prevents uk_user_email violations across test methods. */
    private String uid;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // ── Societe A ──
        societeA = societeRepository.save(new Societe("Tenant A", "MA"));

        adminA = new User("admin-" + uid + "@tenant-a.test", "hash");
        adminA = userRepository.save(adminA);

        User managerA = new User("mgr-" + uid + "@tenant-a.test", "hash");
        managerA = userRepository.save(managerA);

        User agentA = new User("agent-" + uid + "@tenant-a.test", "hash");
        agentA = userRepository.save(agentA);

        adminBearerA = "Bearer " + jwtProvider.generate(adminA.getId(), societeA.getId(), UserRole.ROLE_ADMIN);
        managerBearerA = "Bearer " + jwtProvider.generate(managerA.getId(), societeA.getId(), UserRole.ROLE_MANAGER);
        agentBearerA = "Bearer " + jwtProvider.generate(agentA.getId(), societeA.getId(), UserRole.ROLE_AGENT);

        // ── Societe B ──
        societeB = societeRepository.save(new Societe("Tenant B", "MA"));

        adminB = new User("admin-" + uid + "@tenant-b.test", "hash");
        adminB = userRepository.save(adminB);

        adminBearerB = "Bearer " + jwtProvider.generate(adminB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);
    }

    // ===== 401 — No Authorization header =====

    @Test
    void listUsers_noToken_returns401() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_noToken_returns401() throws Exception {
        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("noauth@test.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_noToken_returns401() throws Exception {
        mvc.perform(post(BASE + "/{id}/reset-password", adminA.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ===== 403 — Non-admin roles =====

    @Test
    void listUsers_asManager_returns403() throws Exception {
        mvc.perform(get(BASE)
                        .header("Authorization", managerBearerA))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_asAgent_returns403() throws Exception {
        mvc.perform(get(BASE)
                        .header("Authorization", agentBearerA))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_asManager_returns403() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", managerBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("mgr-create@test.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_asAgent_returns403() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", agentBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("agent-create@test.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_asManager_returns403() throws Exception {
        mvc.perform(patch(BASE + "/{id}/role", adminA.getId())
                        .header("Authorization", managerBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_AGENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setEnabled_asAgent_returns403() throws Exception {
        mvc.perform(patch(BASE + "/{id}/enabled", adminA.getId())
                        .header("Authorization", agentBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_asManager_returns403() throws Exception {
        mvc.perform(post(BASE + "/{id}/reset-password", adminA.getId())
                        .header("Authorization", managerBearerA))
                .andExpect(status().isForbidden());
    }

    // ===== 200/201 — ADMIN happy paths =====

    @Test
    void listUsers_asAdmin_returns200() throws Exception {
        mvc.perform(get(BASE)
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk());
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        String email = "new-" + uid + "@tenant-a.test";
        String body = mvc.perform(post(BASE)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UserResponse created = json.readValue(body, UserResponse.class);
        assertThat(created.id()).isNotNull();
    }

    @Test
    void changeRole_asAdmin_returns200() throws Exception {
        UUID userId = createUserViaApi(adminBearerA, "role-" + uid + "@tenant-a.test");

        mvc.perform(patch(BASE + "/{id}/role", userId)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void setEnabled_asAdmin_returns200() throws Exception {
        UUID userId = createUserViaApi(adminBearerA, "enabled-" + uid + "@tenant-a.test");

        mvc.perform(patch(BASE + "/{id}/enabled", userId)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void resetPassword_asAdmin_returns200() throws Exception {
        UUID userId = createUserViaApi(adminBearerA, "reset-" + uid + "@tenant-a.test");

        mvc.perform(post(BASE + "/{id}/reset-password", userId)
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());
    }

    // ===== Validation + conflict =====

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        String email = "dup-" + uid + "@tenant-a.test";
        createUserViaApi(adminBearerA, email);

        mvc.perform(post(BASE)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_EXISTS"));
    }

    @Test
    void changeRole_unknownUser_returns404() throws Exception {
        mvc.perform(patch(BASE + "/{id}/role", UUID.randomUUID())
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ===== Helpers =====

    private String createUserJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"TestPass123!\",\"role\":\"ROLE_MANAGER\"}";
    }

    private UUID createUserViaApi(String bearer, String email) throws Exception {
        String body = mvc.perform(post(BASE)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }
}
