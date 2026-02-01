package com.yem.hlm.backend.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @Transactional
    void createTenantReturns201WithLocationAndBody() throws Exception {
        String key = uniqueKey("acme");
        Map<String, Object> payload = Map.of(
                "key", key,
                "name", "Acme Corp",
                "ownerEmail", "owner@acme.com",
                "ownerPassword", "supersecret"
        );

        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/tenants/.*")))
                .andExpect(jsonPath("$.tenant.id").exists())
                .andExpect(jsonPath("$.tenant.key").value(key.toLowerCase()))
                .andExpect(jsonPath("$.tenant.name").value("Acme Corp"))
                .andExpect(jsonPath("$.owner.email").value("owner@acme.com"));
    }

    @Test
    @Transactional
    void createTenantReturns400ForInvalidPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "key", "",
                "name", "",
                "ownerEmail", "not-an-email",
                "ownerPassword", "short"
        );

        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createTenantReturns409ForDuplicateKey() throws Exception {
        String key = uniqueKey("acme");
        Map<String, Object> payload = Map.of(
                "key", key,
                "name", "Acme Corp",
                "ownerEmail", "owner@acme.com",
                "ownerPassword", "supersecret"
        );

        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @Transactional
    void getTenantReturns200WhenTenantMatchesContext() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant(uniqueKey("acme"), "Acme Corp"));
        User owner = userRepository.save(new User(
                tenant,
                "owner@acme.com",
                passwordEncoder.encode("supersecret")
        ));

        String token = jwtProvider.generate(owner.getId(), tenant.getId());
        org.assertj.core.api.Assertions.assertThat(jwtProvider.extractTenantId(token)).isEqualTo(tenant.getId());
        org.assertj.core.api.Assertions.assertThat(jwtProvider.extractUserId(token)).isEqualTo(owner.getId());

        mockMvc.perform(get("/tenants/{id}", tenant.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.id").value(tenant.getId().toString()))
                .andExpect(jsonPath("$.owner.email").value("owner@acme.com"));
    }

    @Test
    @Transactional
    void getTenantReturns403WhenTenantMismatch() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant(uniqueKey("acme"), "Acme Corp"));
        User owner = userRepository.save(new User(
                tenant,
                "owner@acme.com",
                passwordEncoder.encode("supersecret")
        ));

        String token = jwtProvider.generate(owner.getId(), UUID.randomUUID());

        mockMvc.perform(get("/tenants/{id}", tenant.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void getTenantReturns404WhenTenantMissing() throws Exception {
        UUID missingTenantId = UUID.randomUUID();
        String token = jwtProvider.generate(UUID.randomUUID(), missingTenantId);

        mockMvc.perform(get("/tenants/{id}", missingTenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String uniqueKey(String prefix) {
        return (prefix + "-" + UUID.randomUUID())
                .toLowerCase(Locale.ROOT);
    }
}
