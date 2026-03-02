package com.yem.hlm.backend.contact.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single entry in the contact activity timeline.
 *
 * @param timestamp     when the event occurred
 * @param eventType     raw event type string (e.g. DEPOSIT_CREATED, SENT, DEPOSIT_DUE_REMINDER)
 * @param category      broad grouping used by the frontend for icon selection
 * @param summary       human-readable description of the event
 * @param correlationId ID of the related entity (deposit/contract/message)
 */
public record TimelineEventResponse(
        LocalDateTime timestamp,
        String eventType,
        TimelineCategory category,
        String summary,
        UUID correlationId
) {
    public enum TimelineCategory {
        AUDIT, MESSAGE, NOTIFICATION, STATUS_CHANGE
    }
}
