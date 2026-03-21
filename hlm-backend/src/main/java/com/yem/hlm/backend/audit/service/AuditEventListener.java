package com.yem.hlm.backend.audit.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.common.event.ContactCreatedEvent;
import com.yem.hlm.backend.common.event.ContactStatusChangedEvent;
import com.yem.hlm.backend.dashboard.service.DashboardEmitterRegistry;
import com.yem.hlm.backend.usermanagement.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for domain events and records corresponding audit entries.
 * Each handler runs in its own transaction (REQUIRES_NEW) so audit
 * recording is independent of the originating transaction outcome.
 */
@Component
public class AuditEventListener {

    private final CommercialAuditRepository repo;
    private final DashboardEmitterRegistry  emitterRegistry;

    public AuditEventListener(CommercialAuditRepository repo,
                               DashboardEmitterRegistry emitterRegistry) {
        this.repo            = repo;
        this.emitterRegistry = emitterRegistry;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContactCreated(ContactCreatedEvent event) {
        CommercialAuditEvent audit = new CommercialAuditEvent();
        audit.setSocieteId(event.getSocieteId());
        audit.setEventType(AuditEventType.CONTACT_CREATED);
        audit.setActorUserId(event.getActorUserId());
        audit.setCorrelationType("CONTACT");
        audit.setCorrelationId(event.getContactId());
        audit.setPayloadJson("{\"fullName\":\"" + escapeJson(event.getFullName()) + "\"}");
        repo.save(audit);
        emitterRegistry.broadcast(event.getSocieteId(), "dashboard-refresh", "{\"type\":\"CONTACT_CREATED\"}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContactStatusChanged(ContactStatusChangedEvent event) {
        CommercialAuditEvent audit = new CommercialAuditEvent();
        audit.setSocieteId(event.getSocieteId());
        audit.setEventType(AuditEventType.CONTACT_STATUS_CHANGED);
        audit.setActorUserId(event.getActorUserId());
        audit.setCorrelationType("CONTACT");
        audit.setCorrelationId(event.getContactId());
        audit.setPayloadJson("{\"from\":\"" + event.getFrom() + "\",\"to\":\"" + event.getTo() + "\"}");
        repo.save(audit);
    }

    // ── User management events ──────────────────────────────────────────────────

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserInvited(UserInvitedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_INVITED,
                event.getActorUserId(), event.userId,
                "{\"role\":\"" + escapeJson(event.role) + "\"}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserActivated(UserActivatedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_ACTIVATED,
                event.getActorUserId(), event.userId, "{}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_ROLE_CHANGED,
                event.getActorUserId(), event.userId,
                "{\"from\":\"" + escapeJson(event.ancienRole)
                        + "\",\"to\":\"" + escapeJson(event.nouveauRole) + "\"}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRemoved(UserRemovedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_REMOVED,
                event.getActorUserId(), event.userId,
                "{\"raison\":\"" + escapeJson(event.raison) + "\"}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserUpdated(UserUpdatedEvent event) {
        String fields = event.changedFields == null ? "[]"
                : "[" + event.changedFields.stream()
                        .map(f -> "\"" + escapeJson(f) + "\"")
                        .reduce((a, b) -> a + "," + b).orElse("") + "]";
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_UPDATED,
                event.getActorUserId(), event.userId,
                "{\"fields\":" + fields + "}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserUnblocked(UserUnblockedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_UNBLOCKED,
                event.getActorUserId(), event.userId, "{}");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserAnonymized(UserAnonymizedEvent event) {
        saveUserAudit(event.getSocieteId(), AuditEventType.USER_ANONYMIZED,
                event.getActorUserId(), event.userId, "{}");
    }

    private void saveUserAudit(java.util.UUID societeId, AuditEventType type,
                                java.util.UUID actorId, java.util.UUID targetUserId,
                                String payloadJson) {
        CommercialAuditEvent audit = new CommercialAuditEvent();
        audit.setSocieteId(societeId);
        audit.setEventType(type);
        audit.setActorUserId(actorId);
        audit.setCorrelationType("USER");
        audit.setCorrelationId(targetUserId);
        audit.setPayloadJson(payloadJson);
        repo.save(audit);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
