package com.yem.hlm.backend.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base commune pour les tests d’intégration.
 *
 * On utilise un container Postgres *singleton* pour éviter un piège classique :
 * Spring Boot cache le contexte entre classes, mais Testcontainers redémarre
 * parfois le container par classe => l'URL JDBC (port) devient périmée =>
 * "Connection refused".
 */
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    /** Container Postgres démarré une seule fois pour toute la JVM de tests. */
    protected static final SingletonPostgresContainer POSTGRES = SingletonPostgresContainer.getInstance();

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Pour éviter des warnings Hikari sur des connexions fermées durant les tests.
        registry.add("spring.datasource.hikari.maxLifetime", () -> "250000");
    }
}
