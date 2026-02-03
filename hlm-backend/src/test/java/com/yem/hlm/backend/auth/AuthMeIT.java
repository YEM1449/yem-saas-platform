package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.support.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AUTH-10 — IT: /auth/me
 *
 * Contrats attendus:
 * - Sans token => 401
 * - Token invalide => 401
 * - Token valide (avec tid) => 200 + payload cohérent
 * - Token valide MAIS sans tid => 401 (token techniquement ok, mais identité "tenant-aware" impossible)
 *
 * Pourquoi 401 et pas 403?
 * - Ton JwtAuthenticationFilter ignore le token si tid manque (pas d'Authentication dans SecurityContext)
 * - Endpoint protégé => Spring Security répond 401 (non authentifié)
 */
class AuthMeIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;

    // On l'utilise uniquement pour forger un token "valide" MAIS incomplet (sans tid).
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returns200_andUserInfo() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtProvider.generate(userId, tenantId);

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Adapte si ton JSON exact est différent:
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    @Test
    void me_withValidTokenButMissingTidClaim_returns401() throws Exception {
        UUID userId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600);

        // Token signé HS256 => signature OK, dates OK, subject OK
        // MAIS pas de claim "tid" => ton filtre ne va pas authentifier la requête.
        String token = TestJwtConfig.mint(
                jwtEncoder,
                userId.toString(),
                now,
                exp,
                Map.of() // <- aucun tid
        );

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
