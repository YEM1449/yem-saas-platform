package com.yem.hlm.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.support.IntegrationTestBase;

/**
 * Integration tests for /auth/me.
 *
 * <p>Why integration tests?</p>
 * <ul>
 *   <li>Validate the whole pipeline: SecurityConfig -> JwtAuthenticationFilter -> Controller</li>
 *   <li>Run with Testcontainers + Liquibase seed, so we test the real behavior end-to-end</li>
 * </ul>
 */
class AuthMeIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Decode an existing valid token (to reuse userId/tenantId).
    @Autowired JwtDecoder jwtDecoder;

    // Craft custom tokens (e.g., missing tid claim) signed with the same secret.
    @Autowired JwtEncoder jwtEncoder;

    // Matches Liquibase seed in 002-seed-tenant-owner.yaml
    private static final String TENANT_KEY = "acme";
    private static final String EMAIL = "admin@acme.com";
    private static final String PASSWORD = "Admin123!";

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + "this.is.not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returns200AndTenantAndUserIds() throws Exception {
        String accessToken = loginAndGetAccessToken();
        Jwt decoded = jwtDecoder.decode(accessToken);

        UUID expectedUserId = UUID.fromString(decoded.getSubject());
        UUID expectedTenantId = UUID.fromString(decoded.getClaimAsString("tid"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(expectedTenantId.toString()))
                .andExpect(jsonPath("$.userId").value(expectedUserId.toString()));
    }

    @Test
    void me_withValidTokenButMissingTidClaim_returns401() throws Exception {
        // 1) Start from a real valid token to reuse the userId (subject)
        String validToken = loginAndGetAccessToken();
        Jwt decoded = jwtDecoder.decode(validToken);

        // 2) Create a new token that is cryptographically valid, but WITHOUT tenant claim ("tid").
        //    In our SaaS design, tenant context is mandatory for authentication => treat as invalid => 401.
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(decoded.getIssuer() != null ? decoded.getIssuer().toString() : null)
                .subject(decoded.getSubject()) // userId
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60 * 10))
                // Intentionally NOT adding "tid"
                .build();

        String tokenWithoutTid = jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokenWithoutTid))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Calls /auth/login and returns the "accessToken" field.
     */
    private String loginAndGetAccessToken() throws Exception {
        String body = """
                {
                  "tenantKey": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(TENANT_KEY, EMAIL, PASSWORD);

        String responseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode accessTokenNode = root.get("accessToken");

        if (accessTokenNode == null || accessTokenNode.asText().isBlank()) {
            throw new IllegalStateException("Login did not return accessToken. Response was: " + responseJson);
        }

        return accessTokenNode.asText();
    }
}
