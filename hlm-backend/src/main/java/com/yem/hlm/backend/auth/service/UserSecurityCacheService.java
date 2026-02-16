package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Provides cached user security metadata (enabled + tokenVersion) for JWT validation.
 * Cache TTL is 60s (see CacheConfig). On role change / disable, cache is evicted explicitly.
 */
@Service
public class UserSecurityCacheService {

    private final UserRepository userRepository;

    public UserSecurityCacheService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(value = CacheConfig.USER_SECURITY_CACHE, key = "#userId.toString()")
    public UserSecurityInfo getSecurityInfo(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserSecurityInfo(u.isEnabled(), u.getTokenVersion()))
                .orElse(null);
    }

    @CacheEvict(value = CacheConfig.USER_SECURITY_CACHE, key = "#userId.toString()")
    public void evict(UUID userId) {
        // evicts the cache entry
    }
}
