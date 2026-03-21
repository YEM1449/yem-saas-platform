package com.yem.hlm.backend.common.error;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying the standard error response contract.
 * Tests all error scenarios (400, 401, 404) return stable JSON structure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorContractIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String validBearer;

    @BeforeEach
    void setup() {
        Societe societe = societeRepository.save(new Societe("Err Tenant", "MA"));
        User user = new User("err@test.com", "hash");
        user = userRepository.save(user);
        validBearer = "Bearer " + jwtProvider.generate(user.getId(), societe.getId(), UserRole.ROLE_ADMIN);
    }

    @Test
    void missingToken_returns401WithStandardError() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/auth/me"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void invalidToken_returns401WithStandardError() throws Exception {
        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/auth/me"));
    }

    @Test
    void missingResource_returns404WithStandardError() throws Exception {
        UUID nonExistentContactId = UUID.randomUUID();

        mvc.perform(get("/api/contacts/" + nonExistentContactId)
                        .header("Authorization", validBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString(nonExistentContactId.toString())))
                .andExpect(jsonPath("$.path").value("/api/contacts/" + nonExistentContactId))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void validationError_onAuthenticatedEndpoint_returns400() throws Exception {
        var malformedPayload = """
                {
                    "firstName": "Test"
                }
                """;

        mvc.perform(post("/api/contacts")
                        .header("Authorization", validBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
