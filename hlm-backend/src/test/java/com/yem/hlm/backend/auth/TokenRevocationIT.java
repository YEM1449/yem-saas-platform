package com.yem.hlm.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests proving that role changes and account disabling
 * immediately invalidate active JWT tokens (P1 security fix).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenRevocationIT extends IntegrationTestBase {

    private static final String ADMIN_USERS = "/api/admin/users";
    private static final String PROPERTIES = "/api/properties";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private User admin;
    private String adminBearer;

    @BeforeEach
    void setup() {
        tenant = tenantRepository.save(
                new Tenant("rev-" + UUID.randomUUID().toString().substring(0, 8), "Revocation Tenant"));

        admin = new User(tenant, "admin@rev.test", passwordEncoder.encode("Admin123!Secure"));
        admin.setRole(UserRole.ROLE_ADMIN);
        admin = userRepository.save(admin);

        // Generate token with current tokenVersion (0)
        adminBearer = "Bearer " + jwtProvider.generate(
                admin.getId(), tenant.getId(), UserRole.ROLE_ADMIN, admin.getTokenVersion());
    }

    // ===== P1 FIX 1: Role demotion revokes active JWT =====

    @Test
    void roleDemotion_oldToken_returns401() throws Exception {
        // 1) Target user starts as ADMIN
        User target = new User(tenant, "target@rev.test", "hash");
        target.setRole(UserRole.ROLE_ADMIN);
        target = userRepository.save(target);

        String targetBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), UserRole.ROLE_ADMIN, target.getTokenVersion());

        // 2) Target can access admin endpoint with current token
        mvc.perform(get(ADMIN_USERS)
                        .header("Authorization", targetBearer))
                .andExpect(status().isOk());

        // 3) Admin demotes target to AGENT → incrementTokenVersion + evict cache
        mvc.perform(patch(ADMIN_USERS + "/{id}/role", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_AGENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_AGENT"));

        // 4) Old token is now rejected (tokenVersion mismatch) → 401
        mvc.perform(get(ADMIN_USERS)
                        .header("Authorization", targetBearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolePromotion_oldToken_returns401() throws Exception {
        // Even promotion invalidates old token (forces re-login with new role)
        User target = new User(tenant, "promo@rev.test", "hash");
        target.setRole(UserRole.ROLE_AGENT);
        target = userRepository.save(target);

        String targetBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), UserRole.ROLE_AGENT, target.getTokenVersion());

        // Agent can access properties (authenticated endpoint)
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isOk());

        // Admin promotes to MANAGER → incrementTokenVersion + evict cache
        mvc.perform(patch(ADMIN_USERS + "/{id}/role", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_MANAGER\"}"))
                .andExpect(status().isOk());

        // Old token is now rejected → 401
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isUnauthorized());
    }

    // ===== P1 FIX 2: Disabling user invalidates active JWT =====

    @Test
    void disableUser_oldToken_returns401() throws Exception {
        User target = new User(tenant, "disabled@rev.test", "hash");
        target.setRole(UserRole.ROLE_AGENT);
        target = userRepository.save(target);

        String targetBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), UserRole.ROLE_AGENT, target.getTokenVersion());

        // 1) Target can access authenticated endpoint
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isOk());

        // 2) Admin disables target → incrementTokenVersion + evict cache
        mvc.perform(patch(ADMIN_USERS + "/{id}/enabled", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // 3) Old token is now rejected → 401
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disableUser_preventsLogin() throws Exception {
        // Create user with real password
        User target = new User(tenant, "login-test@rev.test", passwordEncoder.encode("TestPass123!"));
        target.setRole(UserRole.ROLE_AGENT);
        target = userRepository.save(target);

        // 1) User can log in
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenant.getKey() + "\",\"email\":\"login-test@rev.test\",\"password\":\"TestPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // 2) Admin disables user
        mvc.perform(patch(ADMIN_USERS + "/{id}/enabled", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        // 3) Login attempt fails → 401
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenant.getKey() + "\",\"email\":\"login-test@rev.test\",\"password\":\"TestPass123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reEnableUser_oldTokenStillRejected_newTokenWorks() throws Exception {
        User target = new User(tenant, "reenable@rev.test", "hash");
        target.setRole(UserRole.ROLE_AGENT);
        target = userRepository.save(target);

        String originalBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), UserRole.ROLE_AGENT, target.getTokenVersion());

        // 1) Disable (tokenVersion 0 → 1)
        mvc.perform(patch(ADMIN_USERS + "/{id}/enabled", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        // 2) Re-enable (tokenVersion 1 → 2)
        mvc.perform(patch(ADMIN_USERS + "/{id}/enabled", target.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        // 3) Original token (tv=0) is still rejected — tokenVersion is now 2
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", originalBearer))
                .andExpect(status().isUnauthorized());

        // 4) New token with current tokenVersion works
        target = userRepository.findById(target.getId()).orElseThrow();
        String newBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), target.getRole(), target.getTokenVersion());

        mvc.perform(get(PROPERTIES)
                        .header("Authorization", newBearer))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_incrementsTokenVersion_oldTokenRejected() throws Exception {
        User target = new User(tenant, "reset-rev@rev.test", passwordEncoder.encode("OldPass123!Sec"));
        target.setRole(UserRole.ROLE_AGENT);
        target = userRepository.save(target);

        String targetBearer = "Bearer " + jwtProvider.generate(
                target.getId(), tenant.getId(), UserRole.ROLE_AGENT, target.getTokenVersion());

        // 1) Token works
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isOk());

        // 2) Admin resets password
        mvc.perform(post(ADMIN_USERS + "/{id}/reset-password", target.getId())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());

        // 3) Old token should be rejected because resetPassword also increments tokenVersion
        mvc.perform(get(PROPERTIES)
                        .header("Authorization", targetBearer))
                .andExpect(status().isUnauthorized());
    }
}
