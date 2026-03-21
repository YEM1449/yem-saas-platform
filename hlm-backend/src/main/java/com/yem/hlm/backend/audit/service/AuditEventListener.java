package com.yem.hlm.backend.audit.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.common.event.ContactCreatedEvent;
import com.yem.hlm.backend.common.event.ContactStatusChangedEvent;
import com.yem.hlm.backend.dashboard.service.DashboardEmitterRegistry;
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

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
