package com.yem.hlm.backend.outbox.domain;

import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbound message record in the async outbox.
 *
 * <p>A message is created with {@link MessageStatus#PENDING} and dispatched
 * by {@code OutboundDispatcherService}. On success it becomes
 * {@link MessageStatus#SENT}; on exhausted retries it becomes
 * {@link MessageStatus#FAILED}.
 *
 * <p>Société isolation: all queries must scope to {@code societeId}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbound_message",
        indexes = {
                @Index(name = "idx_om_tenant_status_retry",
                        columnList = "societe_id,status,next_retry_at"),
                @Index(name = "idx_om_tenant_created",
                        columnList = "societe_id,created_at")
        }
)
public class OutboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_outbound_message_user"))
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private MessageChannel channel;

    /** Email address or phone number of the intended recipient. */
    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    /** Subject line — relevant for EMAIL channel only; null for SMS. */
    @Setter
    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status;

    /** Number of dispatch attempts already made. */
    @Setter
    @Column(name = "retries_count", nullable = false)
    private int retriesCount = 0;

    /** Earliest time at which the next dispatch attempt should be made. */
    @Setter
    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    /** Last provider error message (truncated to 2000 chars). */
    @Setter
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /** Optional: domain type of the linked entity (e.g. "CONTACT", "DEPOSIT"). */
    @Setter
    @Column(name = "correlation_type", length = 50)
    private String correlationType;

    /** Optional: UUID of the linked entity. */
    @Setter
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.nextRetryAt == null) {
            this.nextRetryAt = this.createdAt;
        }
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    public OutboundMessage(UUID societeId, User createdByUser,
                           MessageChannel channel, String recipient,
                           String subject, String body) {
        this.societeId     = societeId;
        this.createdByUser = createdByUser;
        this.channel       = channel;
        this.recipient     = recipient;
        this.subject       = subject;
        this.body          = body;
        this.status        = MessageStatus.PENDING;
        this.retriesCount  = 0;
    }

    // =========================================================================
    // Business methods
    // =========================================================================

    /** Marks the message as successfully sent. */
    public void markSent() {
        this.status  = MessageStatus.SENT;
        this.sentAt  = LocalDateTime.now();
    }

    /**
     * Records a failed dispatch attempt.
     *
     * @param error     provider error message (truncated to 2000 chars)
     * @param nextRetry time for the next attempt; if null the message is marked FAILED permanently
     */
    public void recordFailure(String error, LocalDateTime nextRetry) {
        this.retriesCount++;
        this.lastError = error != null && error.length() > 2000
                ? error.substring(0, 2000) : error;
        if (nextRetry != null) {
            this.nextRetryAt = nextRetry;
            // status stays PENDING so dispatcher picks it up again
        } else {
            this.status = MessageStatus.FAILED;
        }
    }
}
