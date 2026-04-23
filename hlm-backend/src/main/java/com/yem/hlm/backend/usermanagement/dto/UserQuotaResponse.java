package com.yem.hlm.backend.usermanagement.dto;

import com.yem.hlm.backend.usermanagement.domain.UserQuota;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserQuotaResponse(
        UUID userId,
        String month,
        BigDecimal caCible,
        Long ventesCountCible,
        LocalDateTime updatedAt
) {
    public static UserQuotaResponse from(UserQuota q) {
        return new UserQuotaResponse(q.getUserId(), q.getYearMonth(),
                q.getCaCible(), q.getVentesCountCible(), q.getUpdatedAt());
    }

    public static UserQuotaResponse empty(UUID userId, String month) {
        return new UserQuotaResponse(userId, month, null, null, null);
    }
}
