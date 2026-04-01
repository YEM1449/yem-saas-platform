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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * Integration test for POST /auth/login.
 *
 * Validates:
 * - Real JWT generation (not a stub) with correct claims
 * - Standard ErrorResponse JSON on authentication failures
 *
 * Seed data (Liquibase):
 *   societeId = 11111111-1111-1111-1111-111111111111
 *   userId   = 22222222-2222-2222-2222-222222222222
 *   role     = ROLE_ADMIN (fixed by 010-fix-seed-owner-role)
 */
@IntegrationTest
class AuthLoginIT extends IntegrationTestBase {

    private static final UUID SEEDED_SOCIETE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEEDED_USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void login_ok_setsHttpOnlyCookieWithValidJwtAndSuppressesTokenFromBody() throws Exception {
        String body = """
            {

              "email": "admin@acme.com",
              "password": "Admin123!Secure"
            }
            """;

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Token must NOT appear in the JSON body — it is in the httpOnly cookie
                .andExpect(jsonPath("$.accessToken").value(""))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                // httpOnly auth cookie must be present
                .andExpect(header().string("Set-Cookie", containsString("hlm_auth=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andReturn();

        // Extract the JWT from the Set-Cookie header and validate its claims
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String token = extractCookieValue(setCookie, "hlm_auth");

        assertThat(jwtProvider.isValid(token)).isTrue();
        assertThat(jwtProvider.extractSocieteId(token)).isEqualTo(SEEDED_SOCIETE_ID);
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(SEEDED_USER_ID);
        assertThat(jwtProvider.extractRoles(token)).contains("ROLE_ADMIN");
    }

    /** Extracts a named cookie value from a raw {@code Set-Cookie} header string. */
    private static String extractCookieValue(String setCookieHeader, String cookieName) {
        for (String part : setCookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith(cookieName + "=")) {
                return part.substring((cookieName + "=").length());
            }
        }
        throw new AssertionError("Cookie '" + cookieName + "' not found in Set-Cookie: " + setCookieHeader);
    }

    @Test
    void login_wrongPassword_returns401WithErrorResponse() throws Exception {
        String body = """
            {

              "email": "admin@acme.com",
              "password": "WrongPass123!"
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

              "email": "nobody@acme.com",
              "password": "Admin123!Secure"
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
