package com.yem.hlm.backend.notification.api.dto;

import com.yem.hlm.backend.notification.domain.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID refId,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {}
