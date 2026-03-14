package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for POST /auth/login.
 *
 * Validates:
 * - Real JWT generation (not a stub) with correct claims
 * - Standard ErrorResponse JSON on authentication failures
 *
 * Seed data (Liquibase):
 *   tenantId = 11111111-1111-1111-1111-111111111111
 *   userId   = 22222222-2222-2222-2222-222222222222
 *   role     = ROLE_ADMIN (fixed by 010-fix-seed-owner-role)
 */
@IntegrationTest
class AuthLoginIT extends IntegrationTestBase {

    private static final UUID SEEDED_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEEDED_USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void login_ok_returnsValidJwtWithCorrectClaims() throws Exception {
        String body = """
            {
              "tenantKey": "acme",
              "email": "admin@acme.com",
              "password": "Admin123!"
            }
            """;

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        // Extract the token from the JSON response
        String json = result.getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(json, "$.accessToken");

        // Validate the JWT is real and contains correct claims
        assertThat(jwtProvider.isValid(token)).isTrue();
        assertThat(jwtProvider.extractTenantId(token)).isEqualTo(SEEDED_TENANT_ID);
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(SEEDED_USER_ID);
        assertThat(jwtProvider.extractRoles(token)).contains("ROLE_ADMIN");
    }

    @Test
    void login_wrongPassword_returns401WithErrorResponse() throws Exception {
        String body = """
            {
              "tenantKey": "acme",
              "email": "admin@acme.com",
              "password": "WrongPassword!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_unknownEmail_returns401WithErrorResponse() throws Exception {
        String body = """
            {
              "tenantKey": "acme",
              "email": "nobody@acme.com",
              "password": "Admin123!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    @Test
    void createTenantThenLogin_returns200WithValidJwt() throws Exception {
        String key = "repro-" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
              "key": "%s",
              "name": "Repro Tenant",
              "ownerEmail": "owner@repro.com",
              "ownerPassword": "Admin123!Secure"
            }
            """.formatted(key);

        // 1) Create tenant
        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        // 2) Login with owner creds — should be 200, NOT 500
        String loginBody = """
            {
              "tenantKey": "%s",
              "email": "owner@repro.com",
              "password": "Admin123!Secure"
            }
            """.formatted(key);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        // 3) Validate JWT claims
        String token = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accessToken");
        assertThat(jwtProvider.isValid(token)).isTrue();
        assertThat(jwtProvider.extractRoles(token)).contains("ROLE_ADMIN");

        // 4) Call /auth/me with the token — validates end-to-end flow
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").isNotEmpty());
    }

    @Test
    void createTenantThenLoginWrongPassword_returns401() throws Exception {
        String key = "repro2-" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
              "key": "%s",
              "name": "Repro Tenant 2",
              "ownerEmail": "owner@repro2.com",
              "ownerPassword": "Admin123!Secure"
            }
            """.formatted(key);

        mockMvc.perform(post("/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        String loginBody = """
            {
              "tenantKey": "%s",
              "email": "owner@repro2.com",
              "password": "WRONG"
            }
            """.formatted(key);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_wrongTenant_returns401WithErrorResponse() throws Exception {
        String body = """
            {
              "tenantKey": "wrongTenant",
              "email": "admin@acme.com",
              "password": "Admin123!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

}
