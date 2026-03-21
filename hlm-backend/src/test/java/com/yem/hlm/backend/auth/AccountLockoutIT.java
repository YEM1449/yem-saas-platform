package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.support.IntegrationTest;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for account lockout.
 * Uses max-attempts=2 via @TestPropertySource for fast tests.
 * Rate limits are set very high to avoid interference.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.security.lockout.max-attempts=2",
        "app.security.lockout.duration-minutes=15",
        "app.security.rate-limit.login.ip-max=10000",
        "app.security.rate-limit.login.key-max=10000"
})
class AccountLockoutIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired SocieteRepository societeRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final UUID SEEDED_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private User createUser(String email, String password) {
        Societe societe = societeRepository.findById(SEEDED_TENANT_ID)
                .orElseThrow(() -> new IllegalStateException("Seed tenant not found"));
        User user = new User(email, passwordEncoder.encode(password));
        user = userRepository.save(user);
        appUserSocieteRepository.save(new AppUserSociete(
                new AppUserSocieteId(user.getId(), SEEDED_TENANT_ID), "AGENT"));
        return user;
    }

    private String wrongPasswordBody(String email) {
        return """
            {
              
              "email": "%s",
              "password": "WrongPass123!"
            }
            """.formatted(email);
    }

    private String correctPasswordBody(String email, String password) {
        return """
            {
              
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);
    }

    @Test
    void afterMaxAttemptsWithWrongPassword_accountIsLocked() throws Exception {
        String email = "lockout-test-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.com";
        String password = "CorrectPass123!";
        createUser(email, password);

        // First wrong attempt — normal 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Second wrong attempt — triggers lockout (max-attempts=2)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody(email)))
                .andExpect(status().isUnauthorized());

        // Third attempt — account is now locked
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void lockedAccount_correctPassword_stillReturns401AccountLocked() throws Exception {
        String email = "lockout-correct-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.com";
        String password = "CorrectPass123!";
        createUser(email, password);

        // Lock the account by failing twice
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody(email)))
                    .andExpect(status().isUnauthorized());
        }

        // Now try with correct password — should still be ACCOUNT_LOCKED
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctPasswordBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void successfulLoginAfterLockoutExpires_returns200() throws Exception {
        String email = "lockout-expired-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.com";
        String password = "CorrectPass123!";
        User user = createUser(email, password);

        // Lock the account via wrong attempts
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody(email)))
                    .andExpect(status().isUnauthorized());
        }

        // Override lockedUntil to the past to simulate expiry
        User lockedUser = userRepository.findByEmail(email).orElseThrow();
        userRepository.setLockedUntilForTest(lockedUser.getId(), Instant.now().minusSeconds(60));

        // Now login with correct password — should succeed since lock expired
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctPasswordBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void failedLoginThenSuccess_resetsFailedAttempts() throws Exception {
        String email = "lockout-reset-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.com";
        String password = "CorrectPass123!";
        createUser(email, password);

        // One wrong attempt (under threshold of 2)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Then successful login — should reset counter
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctPasswordBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Verify counter was reset in DB
        User afterLogin = userRepository.findByEmail(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(afterLogin.getFailedLoginAttempts()).isZero();
        org.assertj.core.api.Assertions.assertThat(afterLogin.getLockedUntil()).isNull();
    }

    @Test
    void failedLoginAfterLockoutExpires_doesNotImmediatelyReLock() throws Exception {
        String email = "lockout-relock-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.com";
        String password = "CorrectPass123!";
        createUser(email, password);

        // Lock the account via wrong attempts (max-attempts=2)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody(email)))
                    .andExpect(status().isUnauthorized());
        }

        // Simulate lockout expiry by back-dating lockedUntil
        User lockedUser = userRepository.findByEmail(email).orElseThrow();
        userRepository.setLockedUntilForTest(lockedUser.getId(), Instant.now().minusSeconds(60));

        // One wrong attempt after expiry — counter resets to 1 (< max-attempts=2), must NOT be ACCOUNT_LOCKED
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Verify the counter restarted from 1, not re-locked yet
        User afterExpiredFail = userRepository.findByEmail(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(afterExpiredFail.getFailedLoginAttempts()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(afterExpiredFail.isLockedOut()).isFalse();
    }
}
