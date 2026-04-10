package com.yem.hlm.backend.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration — Caffeine (in-process) variant.
 *
 * <p>Active by default. When {@code app.redis.enabled=true} this bean is suppressed
 * and {@link RedisCacheConfig} provides a distributed {@code RedisCacheManager} instead.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class CacheConfig {

    public static final String USER_SECURITY_CACHE = "userSecurity";

    /**
     * Commercial dashboard summary cache.
     * TTL: 30 s. Key includes societeId + effectiveAgentId + from + to + projectId.
     * Max 500 entries (one per unique filter combination per tenant).
     */
    public static final String COMMERCIAL_DASHBOARD_CACHE = "commercialDashboardSummary";

    /**
     * Cash dashboard cache.
     * TTL: 60 s. Key includes societeId + from + to.
     */
    public static final String CASH_DASHBOARD_CACHE = "cashDashboard";

    /**
     * Receivables dashboard cache.
     * TTL: 30 s. Key includes societeId + effectiveAgentId.
     */
    public static final String RECEIVABLES_DASHBOARD_CACHE = "receivablesDashboard";

    /**
     * Home dashboard snapshot — role-scoped per (societeId, actorId, role).
     * TTL: 30 s. Max 1 000 entries.
     */
    public static final String HOME_DASHBOARD_CACHE = "homeDashboard";

    /**
     * Projects cache — societe-scoped project lists.
     * TTL: 60 s. Max 1 000 entries.
     */
    public static final String PROJECTS_CACHE = "projects";

    /**
     * Societes cache — super-admin société lookups.
     * TTL: 120 s. Max 200 entries.
     */
    public static final String SOCIETES_CACHE = "societes";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // User security tokens — 60 s TTL, up to 10 000 active users
        manager.registerCustomCache(USER_SECURITY_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .build());

        // Commercial dashboard summaries — 30 s TTL, up to 500 filter combinations
        manager.registerCustomCache(COMMERCIAL_DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());

        // Cash dashboard — 60 s TTL, up to 200 filter combinations
        manager.registerCustomCache(CASH_DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .build());

        // Receivables dashboard — 30 s TTL, up to 200 filter combinations
        manager.registerCustomCache(RECEIVABLES_DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());

        // Home dashboard snapshot — 30 s TTL, up to 1 000 entries (per societeId+actor+role)
        manager.registerCustomCache(HOME_DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());

        // Projects — 60 s TTL, up to 1 000 entries
        manager.registerCustomCache(PROJECTS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .build());

        // Societes — 120 s TTL, up to 200 entries
        manager.registerCustomCache(SOCIETES_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(120, TimeUnit.SECONDS)
                        .build());

        return manager;
    }
}
