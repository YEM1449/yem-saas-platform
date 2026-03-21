package com.yem.hlm.backend.audit.service;

import com.yem.hlm.backend.audit.api.dto.AuditEventResponse;
import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Records and queries commercial audit events.
 *
 * <h3>Transaction semantics</h3>
 * {@link #record} participates in the caller's transaction (default REQUIRED propagation)
 * so the audit row is committed or rolled back together with the business operation.
 */
@Service
@Transactional(readOnly = true)
public class CommercialAuditService {

    private final CommercialAuditRepository repo;

    public CommercialAuditService(CommercialAuditRepository repo) {
        this.repo = repo;
    }

    /**
     * Appends an audit event within the caller's transaction.
     *
     * @param societeId       société scope
     * @param eventType       the commercial event type
     * @param actorUserId     user who triggered the transition
     * @param correlationType entity type label (e.g. {@code "DEPOSIT"}, {@code "CONTRACT"})
     * @param correlationId   ID of the correlated entity
     * @param payloadJson     optional JSON payload for additional context (may be {@code null})
     */
    @Transactional
    public void record(UUID societeId,
                       AuditEventType eventType,
                       UUID actorUserId,
                       String correlationType,
                       UUID correlationId,
                       String payloadJson) {
        CommercialAuditEvent event = new CommercialAuditEvent();
        event.setSocieteId(societeId);
        event.setEventType(eventType);
        event.setActorUserId(actorUserId);
        event.setCorrelationType(correlationType);
        event.setCorrelationId(correlationId);
        event.setPayloadJson(payloadJson);
        repo.save(event);
    }

    /**
     * Queries audit events for the given société with optional filters.
     *
     * @param limit max results, capped at 500
     */
    public List<AuditEventResponse> search(UUID societeId,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           String correlationType,
                                           UUID correlationId,
                                           int limit) {
        return repo.search(societeId, from, to, correlationType, correlationId,
                        PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(e -> new AuditEventResponse(
                        e.getId(),
                        e.getEventType().name(),
                        e.getActorUserId(),
                        e.getCorrelationType(),
                        e.getCorrelationId(),
                        e.getOccurredAt(),
                        e.getPayloadJson()))
                .toList();
    }
}
