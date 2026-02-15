package com.yem.hlm.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AdminUserController (/api/admin/users).
 * Covers 401/403 RBAC, CRUD as ADMIN, and cross-tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerIT extends IntegrationTestBase {

    private static final String BASE = "/api/admin/users";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    // Tenant A
    private Tenant tenantA;
    private User adminA;
    private String adminBearerA;
    private String managerBearerA;
    private String agentBearerA;

    // Tenant B
    private Tenant tenantB;
    private User adminB;
    private String adminBearerB;

    @BeforeEach
    void setup() {
        // ── Tenant A ──
        tenantA = tenantRepository.save(new Tenant("adm-a-" + UUID.randomUUID().toString().substring(0, 8), "Tenant A"));

        adminA = new User(tenantA, "admin@tenant-a.test", "hash");
        adminA.setRole(UserRole.ROLE_ADMIN);
        adminA = userRepository.save(adminA);

        User managerA = new User(tenantA, "mgr@tenant-a.test", "hash");
        managerA.setRole(UserRole.ROLE_MANAGER);
        managerA = userRepository.save(managerA);

        User agentA = new User(tenantA, "agent@tenant-a.test", "hash");
        agentA.setRole(UserRole.ROLE_AGENT);
        agentA = userRepository.save(agentA);

        adminBearerA = "Bearer " + jwtProvider.generate(adminA.getId(), tenantA.getId(), UserRole.ROLE_ADMIN);
        managerBearerA = "Bearer " + jwtProvider.generate(managerA.getId(), tenantA.getId(), UserRole.ROLE_MANAGER);
        agentBearerA = "Bearer " + jwtProvider.generate(agentA.getId(), tenantA.getId(), UserRole.ROLE_AGENT);

        // ── Tenant B ──
        tenantB = tenantRepository.save(new Tenant("adm-b-" + UUID.randomUUID().toString().substring(0, 8), "Tenant B"));

        adminB = new User(tenantB, "admin@tenant-b.test", "hash");
        adminB.setRole(UserRole.ROLE_ADMIN);
        adminB = userRepository.save(adminB);

        adminBearerB = "Bearer " + jwtProvider.generate(adminB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)); // admin, manager, agent from setup
    }

    @Test
    void listUsers_withSearch_filtersResults() throws Exception {
        mvc.perform(get(BASE).param("q", "mgr@")
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("mgr@tenant-a.test"));
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        String body = mvc.perform(post(BASE)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("new-user@tenant-a.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-user@tenant-a.test"))
                .andExpect(jsonPath("$.role").value("ROLE_MANAGER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UserResponse created = json.readValue(body, UserResponse.class);
        assertThat(created.id()).isNotNull();
    }

    @Test
    void changeRole_asAdmin_returns200() throws Exception {
        // Create a user first
        UUID userId = createUserViaApi(adminBearerA, "role-target@tenant-a.test");

        mvc.perform(patch(BASE + "/{id}/role", userId)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void setEnabled_asAdmin_returns200() throws Exception {
        UUID userId = createUserViaApi(adminBearerA, "enabled-target@tenant-a.test");

        mvc.perform(patch(BASE + "/{id}/enabled", userId)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void resetPassword_asAdmin_returns200() throws Exception {
        UUID userId = createUserViaApi(adminBearerA, "reset-target@tenant-a.test");

        mvc.perform(post(BASE + "/{id}/reset-password", userId)
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());
    }

    // ===== Validation + conflict =====

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        createUserViaApi(adminBearerA, "dup@tenant-a.test");

        mvc.perform(post(BASE)
                        .header("Authorization", adminBearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("dup@tenant-a.test")))
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

    // ===== Cross-tenant isolation =====

    @Test
    void listUsers_tenantIsolation_returnsOnlyOwnUsers() throws Exception {
        // Tenant A list must not contain tenant B users
        String body = mvc.perform(get(BASE)
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UserResponse[] users = json.readValue(body, UserResponse[].class);
        assertThat(users).noneMatch(u -> u.email().contains("tenant-b"));
    }

    @Test
    void changeRole_crossTenant_returns404() throws Exception {
        // Create a user in tenant A
        UUID userInA = createUserViaApi(adminBearerA, "cross-role@tenant-a.test");

        // Tenant B admin tries to change role → 404 (user not found in tenant B)
        mvc.perform(patch(BASE + "/{id}/role", userInA)
                        .header("Authorization", adminBearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_AGENT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setEnabled_crossTenant_returns404() throws Exception {
        UUID userInA = createUserViaApi(adminBearerA, "cross-enable@tenant-a.test");

        mvc.perform(patch(BASE + "/{id}/enabled", userInA)
                        .header("Authorization", adminBearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPassword_crossTenant_returns404() throws Exception {
        UUID userInA = createUserViaApi(adminBearerA, "cross-reset@tenant-a.test");

        mvc.perform(post(BASE + "/{id}/reset-password", userInA)
                        .header("Authorization", adminBearerB))
                .andExpect(status().isNotFound());
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
