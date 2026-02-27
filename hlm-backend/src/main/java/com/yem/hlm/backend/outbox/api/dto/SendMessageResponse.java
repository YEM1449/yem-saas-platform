package com.yem.hlm.backend.outbox.api.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /api/messages}.
 * Returns the newly created outbox message ID for client-side tracking.
 */
public record SendMessageResponse(UUID messageId) {}
