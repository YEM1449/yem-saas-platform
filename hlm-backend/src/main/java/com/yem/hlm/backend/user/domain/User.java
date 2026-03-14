package com.yem.hlm.backend.user.domain;

import jakarta.persistence.*;
import com.yem.hlm.backend.tenant.domain.Tenant;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table (name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name="uk_user_tenant_email", columnNames = {"tenant_id","email"})
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_tenant"))
    private Tenant tenant;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Setter
    @Column (name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Setter
    @Column (name = "enabled", nullable = false)
    private boolean enabled = true;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.ROLE_AGENT;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {}

    public User(Tenant tenant, String email, String passwordHash) {
        this.tenant = tenant;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.ROLE_AGENT;
    }

    public UUID getId() {return id;}
    public Tenant getTenant() {return tenant;}
    public String getEmail() {return email;}
    public String getPasswordHash() {return passwordHash;}
    public boolean isEnabled() {return enabled;}
    public UserRole getRole() {return role;}
    public int getTokenVersion() {return tokenVersion;}
    public int getFailedLoginAttempts() {return failedLoginAttempts;}
    public Instant getLockedUntil() {return lockedUntil;}

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
     */
    public void recordFailedAttempt(int maxAttempts, int lockDurationMinutes) {
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
