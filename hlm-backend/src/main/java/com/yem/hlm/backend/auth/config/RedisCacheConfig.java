package com.yem.hlm.backend.auth.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis distributed cache configuration.
 *
 * <p>Activates when {@code app.redis.enabled=true}. Provides the same logical caches
 * as {@link CacheConfig} (Caffeine) but backed by Redis so that multiple backend
 * instances share a single cache layer.
 *
 * <p>This class also owns the {@link LettuceConnectionFactory} bean because
 * {@code RedisAutoConfiguration} is excluded globally (see {@code application.yml}).
 * That exclusion prevents any Redis connection attempts when Redis is not provisioned.
 *
 * <p>All cache values are serialized as JSON via {@link GenericJackson2JsonRedisSerializer}
 * so they are human-readable in Redis CLI and survive class renames within the same major version.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean
    RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:2000ms}") Duration commandTimeout,
            @Value("${spring.data.redis.connect-timeout:2000ms}") Duration connectTimeout) {
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
        if (!password.isBlank()) {
            serverConfig.setPassword(password);
        }
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(connectTimeout)
                .build();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(ClientOptions.builder().socketOptions(socketOptions).build())
                .build();
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        // Default config: JSON values, no null caching, key prefix = cache name + "::"
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // Per-cache TTL overrides (same TTLs as CacheConfig / Caffeine)
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put(CacheConfig.USER_SECURITY_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(60)));

        cacheConfigs.put(CacheConfig.COMMERCIAL_DASHBOARD_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(30)));

        cacheConfigs.put(CacheConfig.CASH_DASHBOARD_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(60)));

        cacheConfigs.put(CacheConfig.RECEIVABLES_DASHBOARD_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(30)));

        cacheConfigs.put(CacheConfig.HOME_DASHBOARD_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(30)));

        cacheConfigs.put(CacheConfig.DASHBOARD_COCKPIT_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(60)));

        cacheConfigs.put(CacheConfig.PROJECTS_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(60)));

        cacheConfigs.put(CacheConfig.SOCIETES_CACHE,
                defaultConfig.entryTtl(Duration.ofSeconds(120)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
