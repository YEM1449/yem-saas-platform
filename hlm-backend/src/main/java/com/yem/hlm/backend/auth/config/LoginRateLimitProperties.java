package com.yem.hlm.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for login-specific rate limiting.
 * Supports two independent buckets: per-IP and per-identity (tenantKey:email).
 */
@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit.login")
public class LoginRateLimitProperties {

    /** Maximum login attempts allowed per IP within the window. */
    private int ipMax = 20;

    /** Maximum login attempts allowed per tenantKey+email within the window. */
    private int keyMax = 10;

    /** Sliding window size in seconds. */
    private int windowSeconds = 60;

    public int getIpMax() { return ipMax; }
    public void setIpMax(int ipMax) { this.ipMax = ipMax; }

    public int getKeyMax() { return keyMax; }
    public void setKeyMax(int keyMax) { this.keyMax = keyMax; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
}
