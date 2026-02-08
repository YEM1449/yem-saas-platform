package com.yem.hlm.backend;

import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
/**
 * Smoke test: vérifie que l'application démarre correctement
 * avec l'infra de test (Postgres Testcontainers + Liquibase).
 */
@SpringBootTest
@ActiveProfiles("test")
class HlmBackendApplicationIT extends IntegrationTestBase {

	@Test
	void contextLoads() {
		// Si le contexte démarre, ce test passe.
		// Sinon: problème infra (DB, liquibase, config, beans).
	}

}
