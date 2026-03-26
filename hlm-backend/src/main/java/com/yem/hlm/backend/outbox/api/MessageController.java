package com.yem.hlm.backend.outbox.api;

import com.yem.hlm.backend.outbox.api.dto.MessageResponse;
import com.yem.hlm.backend.outbox.api.dto.SendMessageRequest;
import com.yem.hlm.backend.outbox.api.dto.SendMessageResponse;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.MessageStatus;
import com.yem.hlm.backend.outbox.service.MessageComposeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST API for the outbound message outbox.
 *
 * <p>Endpoint summary:
 * <ul>
 *   <li>{@code POST /api/messages} — compose a new SMS or Email message (returns 202 Accepted)</li>
 *   <li>{@code GET /api/messages}  — list messages for the current tenant with optional filters</li>
 * </ul>
 *
 * <p>Note: This controller intentionally lives at {@code /api/messages} to avoid ambiguity
 * with the existing {@code /api/notifications} in-app notification endpoint.
 */
@Tag(name = "Messages", description = "Outbound SMS and email via the transactional outbox")
@RestController
@RequestMapping("/api/messages")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
public class MessageController {

    private final MessageComposeService messageService;

    public MessageController(MessageComposeService messageService) {
        this.messageService = messageService;
    }

    /**
     * Compose and queue a new outbound message.
     *
     * <p>Only inserts a PENDING record — actual dispatch happens asynchronously.
     *
     * @return 202 Accepted with the new {@code messageId}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SendMessageResponse send(@Valid @RequestBody SendMessageRequest request) {
        return messageService.compose(request);
    }

    /**
     * List outbound messages for the current tenant.
     * Results are always scoped to the authenticated tenant (multi-tenant isolation).
     *
     * @param channel       optional channel filter (EMAIL | SMS)
     * @param status        optional status filter (PENDING | SENT | FAILED)
     * @param contactId     optional contact UUID — returns messages correlated with this contact
     * @param from          optional start of creation window (ISO datetime)
     * @param to            optional end of creation window (ISO datetime)
     * @param page          zero-based page number (default 0)
     * @param size          page size, 1–200 (default 20)
     */
    @GetMapping
    public Page<MessageResponse> list(
            @RequestParam(required = false) MessageChannel channel,
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return messageService.list(channel, status, contactId, from, to, page, size);
    }
}
