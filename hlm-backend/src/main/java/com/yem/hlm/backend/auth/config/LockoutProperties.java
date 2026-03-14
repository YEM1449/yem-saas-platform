package com.yem.hlm.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for account lockout after repeated failed login attempts.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.lockout")
public class LockoutProperties {

    /** Number of consecutive failed login attempts before locking the account. */
    private int maxAttempts = 5;

    /** Duration in minutes for which the account remains locked. */
    private int durationMinutes = 15;

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
}
