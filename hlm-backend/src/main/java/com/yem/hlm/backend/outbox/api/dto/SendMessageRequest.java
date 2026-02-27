package com.yem.hlm.backend.outbox.api.dto;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /api/messages}.
 *
 * <p>Recipient resolution precedence:
 * <ol>
 *   <li>If {@code contactId} is supplied the recipient is derived from the contact's
 *       email (EMAIL channel) or phone (SMS channel).</li>
 *   <li>Otherwise {@code recipient} must be provided and is validated by the service.</li>
 * </ol>
 */
public record SendMessageRequest(

        @NotNull
        MessageChannel channel,

        /** Preferred: resolve recipient from an existing CRM contact. */
        UUID contactId,

        /** Explicit recipient (email or phone). Used when contactId is null. */
        String recipient,

        /** Subject line — relevant for EMAIL only; ignored for SMS. */
        @Size(max = 500)
        String subject,

        @NotBlank
        @Size(max = 4000)
        String body,

        /** Optional: domain type of the linked entity (e.g. "CONTACT", "DEPOSIT"). */
        @Size(max = 50)
        String correlationType,

        /** Optional: UUID of the linked entity for traceability. */
        UUID correlationId
) {}
