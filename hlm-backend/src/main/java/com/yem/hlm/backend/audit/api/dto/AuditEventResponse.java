package com.yem.hlm.backend.audit.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a {@code CommercialAuditEvent} row.
 * Returned by {@code GET /api/audit/commercial}.
 */
public record AuditEventResponse(
        UUID id,
        String eventType,
        UUID actorUserId,
        String correlationType,
        UUID correlationId,
        LocalDateTime occurredAt,
        String payloadJson
) {}
