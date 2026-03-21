package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.support.TestJwtConfig;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IT: /auth/me
 * - No token => 401
 * - Invalid token => 401
 * - Valid token (with sid) => 200
 * - Valid token without sid => 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthMeIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private User user;
    private Societe societe;

    @BeforeEach
    void setup() {
        societe = societeRepository.save(new Societe("Me Tenant", "MA"));
        user = new User("me@test.com", "hash");
        user = userRepository.save(user);
    }

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
        String token = jwtProvider.generate(user.getId(), societe.getId());

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.societeId").value(societe.getId().toString()));
    }

    @Test
    void me_withValidTokenButMissingTidClaim_returns401() throws Exception {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600);

        // Signed token, valid subject, but no sid claim => filter skips auth
        String token = TestJwtConfig.mint(
                jwtEncoder,
                user.getId().toString(),
                now,
                exp,
                Map.of()
        );

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
