package com.yem.hlm.backend.outbox.api.dto;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.MessageStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-model for a single outbound message — used in list responses.
 */
public record MessageResponse(
        UUID id,
        MessageChannel channel,
        MessageStatus status,
        String recipient,
        String subject,
        String body,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        int retriesCount,
        String lastError,
        String correlationType,
        UUID correlationId,
        /** UUID of the CRM user who composed this message. */
        UUID createdByUserId
) {}
