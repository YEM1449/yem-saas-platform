package com.yem.hlm.backend.payment.api.dto;

import com.yem.hlm.backend.payment.domain.PaymentSchedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentScheduleResponse(
        UUID id,
        UUID contractId,
        String notes,
        LocalDateTime createdAt,
        List<TrancheResponse> tranches
) {
    public static PaymentScheduleResponse from(PaymentSchedule ps) {
        return new PaymentScheduleResponse(
                ps.getId(),
                ps.getSaleContract().getId(),
                ps.getNotes(),
                ps.getCreatedAt(),
                ps.getTranches().stream().map(TrancheResponse::from).toList()
        );
    }
}
