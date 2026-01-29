package com.yem.hlm.backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests using a cloud PostgreSQL database (Supabase).
 *
 * - Datasource is defined in application-cloudtest.yml
 * - Uses Spring profile: "cloudtest"
 * - No Docker required
 */
@ActiveProfiles("cloudtest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class CloudIntegrationTestBase {
}
