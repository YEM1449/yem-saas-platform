package com.yem.hlm.backend.audit.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit record for key commercial lifecycle transitions.
 * Persisted via {@link com.yem.hlm.backend.audit.service.CommercialAuditService#record}.
 *
 * <p>Never updated after creation — audit records are immutable by design.
 */
@Entity
@Table(name = "commercial_audit_event")
public class CommercialAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private AuditEventType eventType;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "correlation_type", length = 50)
    private String correlationType;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @PrePersist
    void onPersist() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public UUID getId()                    { return id; }
    public Tenant getTenant()              { return tenant; }
    public AuditEventType getEventType()   { return eventType; }
    public UUID getActorUserId()           { return actorUserId; }
    public String getCorrelationType()     { return correlationType; }
    public UUID getCorrelationId()         { return correlationId; }
    public LocalDateTime getOccurredAt()   { return occurredAt; }
    public String getPayloadJson()         { return payloadJson; }

    public void setTenant(Tenant tenant)                 { this.tenant = tenant; }
    public void setEventType(AuditEventType eventType)   { this.eventType = eventType; }
    public void setActorUserId(UUID actorUserId)         { this.actorUserId = actorUserId; }
    public void setCorrelationType(String ct)            { this.correlationType = ct; }
    public void setCorrelationId(UUID correlationId)     { this.correlationId = correlationId; }
    public void setPayloadJson(String payloadJson)       { this.payloadJson = payloadJson; }
}
