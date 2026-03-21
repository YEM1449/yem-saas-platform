package com.yem.hlm.backend.user.domain;

import jakarta.persistence.*;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = {"email"})
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Setter
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Platform-level role (nullable — société-specific roles live in AppUserSociete). */
    @Column(name = "platform_role", length = 50)
    private String platformRole;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public String getPlatformRole() { return platformRole; }
    public void setPlatformRole(String platformRole) { this.platformRole = platformRole; }
    public int getTokenVersion() { return tokenVersion; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    /**
     * Returns true if the account is currently locked out.
     */
    public boolean isLockedOut() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Records a failed login attempt. If the number of consecutive failures
     * reaches maxAttempts, the account is locked for lockDurationMinutes.
     * If a previous lockout has already expired, the counter is reset first
     * so an expired lockout does not cause an immediate re-lock on the next failure.
     */
    public void recordFailedAttempt(int maxAttempts, int lockDurationMinutes) {
        if (lockedUntil != null && !Instant.now().isBefore(lockedUntil)) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plusSeconds((long) lockDurationMinutes * 60);
        }
    }

    /**
     * Resets the failed login counter and clears the lockout (called on successful login).
     */
    public void resetLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
