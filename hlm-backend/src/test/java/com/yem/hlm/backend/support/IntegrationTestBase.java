package com.yem.hlm.backend.support;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests.
 *
 * Tech goal:
 * - Start a real Postgres in a container (isolated DB)
 * - Inject its JDBC properties into Spring Boot at runtime
 * - Run Liquibase migrations automatically
 */
@Testcontainers
// ✅ Active l’intégration JUnit 5 ↔ Testcontainers.
// Concrètement : les containers démarrent/stop automatiquement pour tes tests.

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
// ✅ Démarre le Spring ApplicationContext complet (Security, MVC, DB, etc.)
// MAIS sans ouvrir un vrai port HTTP.
// Spring va simuler les appels HTTP via le DispatcherServlet (mode "mock server").

@AutoConfigureMockMvc
// ✅ Demande explicitement à Spring de créer le bean MockMvc.
// Sans ça, @Autowired MockMvc = NoSuchBeanDefinitionException.

@ActiveProfiles("test")
// ✅ Active le profil "test" → Spring charge application-test.yml.
// But : éviter d’utiliser application-local.yml ou application-cloudtest.yml par erreur.

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// ✅ Permet d’avoir des méthodes @BeforeAll non statiques.
// Et aussi de garder un état partagé par classe si nécessaire.
// (Optionnel : si tu n’en as pas besoin, tu peux l’enlever.)

public abstract class IntegrationTestBase {

    /**
     * Static container = 1 container partagé pour la classe de test,
     * ce qui accélère énormément les tests.
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hlm_test") // nom DB dans le container
                    .withUsername("hlm")         // user DB
                    .withPassword("hlm");        // password DB

    static {
        // Démarre le container au chargement de la classe
        POSTGRES.start();
    }

    /**
     * Injecte dynamiquement les properties DB dans Spring Boot.
     * Ce mécanisme override application.yml/test.yml.
     */
    @DynamicPropertySource
    static void registerDatasourceProps(DynamicPropertyRegistry registry) {
        // URL JDBC du container
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);

        // credentials du container
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // driver
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Liquibase ON: on veut que le schéma soit appliqué pour chaque run
        registry.add("spring.liquibase.enabled", () -> "true");
    }
}
