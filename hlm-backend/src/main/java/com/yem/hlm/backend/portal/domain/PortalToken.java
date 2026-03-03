package com.yem.hlm.backend.portal.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time magic link token for buyer portal access.
 *
 * <p>Only the SHA-256 hash of the raw token is stored — the raw token is
 * transmitted via email and never persisted.
 *
 * <p>Token lifecycle: PENDING (usedAt == null) → USED (usedAt set, one-time).
 * Expired tokens (expiresAt in the past) are also invalid.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "portal_token",
        indexes = {
                @Index(name = "idx_pt_tenant_contact", columnList = "tenant_id,contact_id"),
                @Index(name = "idx_pt_expires_at",     columnList = "expires_at")
        }
)
public class PortalToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portal_token_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portal_token_contact"))
    private Contact contact;

    /** SHA-256 hex digest of the raw token (stored instead of the raw value). */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is consumed. Null = still valid (if not expired). */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public PortalToken(Tenant tenant, Contact contact, String tokenHash, Instant expiresAt) {
        this.tenant    = tenant;
        this.contact   = contact;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** Returns true when the token is still usable (not expired and not used). */
    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    /** Marks the token as consumed (one-time use). */
    public void markUsed() {
        this.usedAt = Instant.now();
    }
}
