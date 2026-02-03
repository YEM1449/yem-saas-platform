package com.yem.hlm.backend.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base commune pour les tests d’intégration (IT).
 *
 * Objectifs :
 * - Démarrer un Postgres éphémère via Testcontainers
 * - Injecter dynamiquement les propriétés datasource Spring au démarrage du contexte
 * - Garantir un environnement reproductible (profil "test")
 *
 * IMPORTANT :
 * - @Testcontainers est indispensable ici, sinon le container ne démarre pas
 *   et Spring tente de lire getJdbcUrl() => "Mapped port can only be obtained..."
 * - On n’ajoute PAS @SpringBootTest ici : certains tests peuvent être @WebMvcTest
 *   ou d’autres slices. Chaque IT choisit son style d’initialisation.
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    /**
     * Container Postgres partagé pour la classe de test.
     *
     * Notes “senior” :
     * - static : un seul container pour tous les tests d’une même JVM (plus rapide).
     * - @Container : géré par l’extension JUnit Testcontainers.
     * - Image : garde une version stable (évite latest).
     */
    @Container
    @SuppressWarnings("resource") // géré par Testcontainers
    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hlm_test")
                    .withUsername("test")
                    .withPassword("test");

    /**
     * Injection dynamique des propriétés Spring *avant* l'initialisation du contexte.
     *
     * Crucial : à ce moment, on a besoin que le container soit déjà "started",
     * ce que garantit @Testcontainers + @Container.
     */
    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Optionnel mais utile en test :
        // - si tu relies Liquibase à la même datasource, c’est déjà implicite.
        // - sinon tu peux aussi fixer : registry.add("spring.liquibase.enabled", () -> "true");
    }
}
