package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.support.IntegrationTest;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Swagger/OpenAPI endpoints are disabled when the production
 * profile is active and that the actuator health endpoint remains accessible.
 *
 * Uses @TestPropertySource to simulate production springdoc config
 * without creating a new Spring context that would miss the test datasource.
 */
@IntegrationTest
@ActiveProfiles({"test", "production"})
class SwaggerProductionIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;

    @Test
    void swaggerUi_isNotAvailableInProduction() throws Exception {
        // With springdoc.swagger-ui.enabled=false, the UI is not served
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // SpringDoc disabled: expects 404 (not found) or a non-200 redirect
                    org.assertj.core.api.Assertions.assertThat(status)
                            .as("Swagger UI must not return 200 in production")
                            .isNotEqualTo(200);
                });
    }

    @Test
    void apiDocs_isNotAvailableInProduction() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status)
                            .as("OpenAPI docs must not return 200 in production")
                            .isNotEqualTo(200);
                });
    }

    @Test
    void actuatorHealth_isStillAvailableInProduction() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // 200 = healthy, 503 = unhealthy but endpoint accessible
                    org.assertj.core.api.Assertions.assertThat(status)
                            .as("Actuator health endpoint must be accessible (not 404/401/403) in production")
                            .isIn(200, 503);
                });
    }
}
