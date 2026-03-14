package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.support.IntegrationTest;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Integration test for login rate limiting.
 *
 * Uses ip-max=3 and key-max=2 for fast test execution.
 * Each test uses a distinct IP (via X-Forwarded-For) and/or email to avoid cross-test interference.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.security.rate-limit.login.ip-max=3",
        "app.security.rate-limit.login.key-max=2",
        "app.security.rate-limit.login.window-seconds=60"
})
class LoginRateLimitIT extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    private String loginBody(String tenant, String email) {
        return """
            {
              "tenantKey": "%s",
              "email": "%s",
              "password": "WrongPassword!"
            }
            """.formatted(tenant, email);
    }

    @Test
    void ipLimitExceeded_returns429WithLoginRateLimited() throws Exception {
        String ip = "10.0.1.1";
        String email = "ratelimit-ip@acme.com";

        // First 3 requests are allowed (ip-max=3)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", ip)
                            .content(loginBody("acme", email + i)))
                    .andExpect(status().isUnauthorized()); // wrong password but not rate limited
        }

        // 4th request from same IP should be rate limited
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", ip)
                        .content(loginBody("acme", email + "extra")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void identityLimitExceeded_returns429WithLoginRateLimited() throws Exception {
        String ip = "10.0.2.1";
        String email = "ratelimit-key@acme.com";

        // First 2 requests with same tenantKey+email are allowed (key-max=2)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", ip + i) // different IPs to avoid IP limit
                            .content(loginBody("acme", email)))
                    .andExpect(status().isUnauthorized());
        }

        // 3rd request with same identity should be rate limited
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.0.2.99")
                        .content(loginBody("acme", email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void differentIp_notRateLimited_whenFirstIpIsLimited() throws Exception {
        String limitedIp = "10.0.3.1";
        String cleanIp = "10.0.3.200";

        // Exhaust the rate limit for limitedIp
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", limitedIp)
                            .content(loginBody("acme", "diff-ip-test-" + i + "@acme.com")))
                    .andExpect(status().isUnauthorized());
        }
        // Verify limitedIp is now blocked
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", limitedIp)
                        .content(loginBody("acme", "diff-ip-blocked@acme.com")))
                .andExpect(status().isTooManyRequests());

        // A different IP should NOT be rate-limited
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", cleanIp)
                        .content(loginBody("acme", "admin@acme.com")))
                .andExpect(status().isUnauthorized()); // 401 = not rate limited, just wrong password
    }

    @Test
    void normalUsage_underIpMax_returns401NotRateLimited() throws Exception {
        String ip = "10.0.4.1";

        // 3 requests (exactly ip-max) should all get normal 401
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", ip)
                            .content(loginBody("acme", "normal-" + i + "@acme.com")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
