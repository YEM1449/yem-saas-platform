package com.yem.hlm.backend.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.portal.service.PortalJwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.UserRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Portal Authentication (magic link flow).
 *
 * Tests:
 * 1) requestLink_knownContact_returns200AndMagicLinkUrl
 * 2) verifyToken_validToken_returnsPortalJwt
 * 3) verifyToken_alreadyUsedToken_returns401
 * 4) verifyToken_unknownToken_returns401
 * 5) portalJwt_cannotAccess_crmEndpoint
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalAuthIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired PortalJwtProvider portalJwtProvider;

    private String adminBearer;

    @BeforeEach
    void setup() throws Exception {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);

        // Create a contact to use as buyer in magic-link requests
        var req = new CreateContactRequest("Portal", "Buyer", null, "portal-buyer@acme.com",
                null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 1. Request link with known contact email → 200 with magicLinkUrl
    // =========================================================================

    @Test
    void requestLink_knownContact_returns200AndMagicLinkUrl() throws Exception {
        String body = "{\"email\":\"portal-buyer@acme.com\",\"tenantKey\":\"acme\"}";
        String json = mvc.perform(post("/api/portal/auth/request-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magicLinkUrl").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("magicLinkUrl").asText()).contains("/portal/login?token=");
    }

    // =========================================================================
    // 2. Verify valid token → 200 with accessToken (portal JWT)
    // =========================================================================

    @Test
    void verifyToken_validToken_returnsPortalJwt() throws Exception {
        String rawToken = requestMagicLink("portal-buyer@acme.com");

        String json = mvc.perform(get("/api/portal/auth/verify")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(json).get("accessToken").asText();
        assertThat(portalJwtProvider.isValid(accessToken)).isTrue();
    }

    // =========================================================================
    // 3. Verify already-used token → 401
    // =========================================================================

    @Test
    void verifyToken_alreadyUsedToken_returns401() throws Exception {
        String rawToken = requestMagicLink("portal-buyer@acme.com");

        // First use — success
        mvc.perform(get("/api/portal/auth/verify").param("token", rawToken))
                .andExpect(status().isOk());

        // Second use — 401
        mvc.perform(get("/api/portal/auth/verify").param("token", rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PORTAL_TOKEN_INVALID"));
    }

    // =========================================================================
    // 4. Verify unknown token → 401
    // =========================================================================

    @Test
    void verifyToken_unknownToken_returns401() throws Exception {
        mvc.perform(get("/api/portal/auth/verify").param("token", "not-a-real-token-xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PORTAL_TOKEN_INVALID"));
    }

    // =========================================================================
    // 5. Portal JWT cannot access CRM endpoints
    // =========================================================================

    @Test
    void portalJwt_cannotAccess_crmEndpoint() throws Exception {
        String rawToken = requestMagicLink("portal-buyer@acme.com");
        String portalJwt = verifyAndGetJwt(rawToken);

        mvc.perform(get("/api/contacts")
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Requests a magic link for the given email and returns the raw token. */
    private String requestMagicLink(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"tenantKey\":\"acme\"}";
        String json = mvc.perform(post("/api/portal/auth/request-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String magicLinkUrl = objectMapper.readTree(json).get("magicLinkUrl").asText();
        // Extract raw token from URL: .../portal/login?token=<rawToken>
        return magicLinkUrl.substring(magicLinkUrl.indexOf("?token=") + 7);
    }

    /** Verifies a raw token and returns the portal JWT string. */
    private String verifyAndGetJwt(String rawToken) throws Exception {
        String json = mvc.perform(get("/api/portal/auth/verify").param("token", rawToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }
}
