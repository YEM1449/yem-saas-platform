package com.yem.hlm.backend.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton Postgres container for all integration tests.
 *
 * Why: Spring's context caching + Testcontainers per-class lifecycle can lead to
 * stale JDBC URLs (old port) and "Connection refused" when a container is
 * restarted between test classes.
 *
 * This singleton container starts once per JVM and is never stopped by JUnit.
 */
public final class SingletonPostgresContainer extends PostgreSQLContainer<SingletonPostgresContainer> {

    private static final String IMAGE = "postgres:16-alpine";
    private static volatile SingletonPostgresContainer instance;

    private SingletonPostgresContainer() {
        super(IMAGE);
        withDatabaseName("hlm_test");
        withUsername("test");
        withPassword("test");
    }

    public static SingletonPostgresContainer getInstance() {
        if (instance == null) {
            synchronized (SingletonPostgresContainer.class) {
                if (instance == null) {
                    instance = new SingletonPostgresContainer();
                    instance.start();
                }
            }
        }
        return instance;
    }

    /**
     * Disable stop to keep container running for the whole JVM.
     */
    @Override
    public void stop() {
        // no-op
    }
}
