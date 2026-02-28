package com.yem.hlm.backend.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_SECURITY_CACHE = "userSecurity";

    /**
     * Commercial dashboard summary cache.
     * TTL: 30 s. Key includes tenantId + effectiveAgentId + from + to + projectId.
     * Max 500 entries (one per unique filter combination per tenant).
     */
    public static final String COMMERCIAL_DASHBOARD_CACHE = "commercialDashboardSummary";

    /**
     * Cash dashboard cache.
     * TTL: 60 s. Key includes tenantId + from + to.
     */
    public static final String CASH_DASHBOARD_CACHE = "cashDashboard";

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

        return manager;
    }
}
