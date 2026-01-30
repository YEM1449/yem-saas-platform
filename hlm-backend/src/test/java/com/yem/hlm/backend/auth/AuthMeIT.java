package com.yem.hlm.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthMeIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer invalid.token.here")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returns200_andReturnsTenantAndUserIds() throws Exception {

        // 1) Login -> récupérer accessToken
        String loginBody = """
            {
              "tenantKey": "acme",
              "email": "admin@acme.com",
              "password": "Admin123!"
            }
            """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode loginRoot = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginRoot.get("accessToken").asText();
        assertNotNull(token);
        assertFalse(token.isBlank());

        // 2) /auth/me avec Bearer token
        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").isNotEmpty())
                .andReturn();

        // 3) Vérifier que userId/tenantId sont bien des UUID (=> TenantContext rempli par le filtre)
        JsonNode meRoot = objectMapper.readTree(meResult.getResponse().getContentAsString());

        String userId = meRoot.get("userId").asText();
        String tenantId = meRoot.get("tenantId").asText();

        assertDoesNotThrow(() -> UUID.fromString(userId), "userId doit être un UUID valide");
        assertDoesNotThrow(() -> UUID.fromString(tenantId), "tenantId doit être un UUID valide");
    }

    @Test
    void me_withValidToken_butMissingTidClaim_returns401() throws Exception {
        Instant now = Instant.now();

        // token "cryptographiquement valide" (signé), mais incomplet pour notre app (pas de tid)
        String tokenWithoutTid = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwtClaimsSet.builder()
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(3600))
                                .subject(UUID.randomUUID().toString()) // userId
                                // .claim("tid", "...")  <-- volontairement absent
                                .build()
                )
        ).getTokenValue();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokenWithoutTid)
                )
                .andExpect(status().isUnauthorized());
    }
}
