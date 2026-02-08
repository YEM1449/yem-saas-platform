package com.yem.hlm.backend.notification.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_tenant_recipient_read", columnList = "tenant_id,recipient_user_id,is_read"),
                @Index(name = "idx_notification_tenant_recipient_created", columnList = "tenant_id,recipient_user_id,created_at")
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_recipient"))
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "ref_id")
    private UUID refId;

    /**
     * Stored as JSONB in PostgreSQL.
     * We keep it as a String to avoid adding extra dependencies.
     */
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Notification(Tenant tenant, User recipientUser, NotificationType type, UUID refId, String payload) {
        this.tenant = tenant;
        this.recipientUser = recipientUser;
        this.type = type;
        this.refId = refId;
        this.payload = payload;
    }

    public void markRead() {
        this.read = true;
    }
}
