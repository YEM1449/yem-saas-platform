package com.yem.hlm.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSwitchSocieteIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;
    @Autowired UserSecurityCacheService userSecurityCacheService;

    private User user;
    private Societe societeA;
    private Societe societeB;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        societeA = societeRepository.saveAndFlush(new Societe("Switch Alpha " + suffix, "MA"));
        societeB = societeRepository.saveAndFlush(new Societe("Switch Beta " + suffix, "MA"));

        user = userRepository.saveAndFlush(new User("switch-" + suffix + "@test.com", passwordEncoder.encode("Admin123!Secure")));
        enroll(user.getId(), societeA.getId(), "ADMIN");
        enroll(user.getId(), societeB.getId(), "MANAGER");
        appUserSocieteRepository.flush();
    }

    @Test
    void login_withMultipleSocietes_returnsPartialTokenAndChoices() throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Admin123!Secure"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresSocieteSelection").value(true))
                .andExpect(jsonPath("$.tokenType").value("Partial"))
                .andExpect(jsonPath("$.societes.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String partialToken = json.readTree(body).get("accessToken").asText();
        assertThat(jwtProvider.isPartialToken(partialToken)).isTrue();
        assertThat(jwtProvider.extractUserId(partialToken)).isEqualTo(user.getId());
        assertThat(jwtProvider.extractTokenVersion(partialToken)).isEqualTo(user.getTokenVersion());
    }

    @Test
    void switchSociete_withPartialToken_returnsScopedJwt() throws Exception {
        JsonNode login = loginForSelection();
        String partialToken = login.get("accessToken").asText();

        String body = mvc.perform(post("/auth/switch-societe")
                        .header("Authorization", "Bearer " + partialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"societeId":"%s"}
                                """.formatted(societeB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.requiresSocieteSelection").value(false))
                .andReturn().getResponse().getContentAsString();

        String fullToken = json.readTree(body).get("accessToken").asText();
        assertThat(jwtProvider.isPartialToken(fullToken)).isFalse();
        assertThat(jwtProvider.extractSocieteId(fullToken)).isEqualTo(societeB.getId());
    }

    @Test
    void switchSociete_rejectsDisabledUserEvenWithValidPartialToken() throws Exception {
        JsonNode login = loginForSelection();
        String partialToken = login.get("accessToken").asText();

        user.setEnabled(false);
        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);
        userSecurityCacheService.evict(user.getId());

        mvc.perform(post("/auth/switch-societe")
                        .header("Authorization", "Bearer " + partialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"societeId":"%s"}
                                """.formatted(societeA.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("TOKEN_INVALIDATED"));
    }

    @Test
    void switchSociete_rejectsStalePartialTokenAfterTokenVersionChange() throws Exception {
        JsonNode login = loginForSelection();
        String partialToken = login.get("accessToken").asText();

        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);
        userSecurityCacheService.evict(user.getId());

        mvc.perform(post("/auth/switch-societe")
                        .header("Authorization", "Bearer " + partialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"societeId":"%s"}
                                """.formatted(societeA.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("TOKEN_INVALIDATED"));
    }

    private JsonNode loginForSelection() throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Admin123!Secure"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresSocieteSelection").value(true))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private void enroll(UUID userId, UUID societeId, String role) {
        appUserSocieteRepository.saveAndFlush(new AppUserSociete(new AppUserSocieteId(userId, societeId), role));
    }
}
