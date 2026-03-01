package com.yem.hlm.backend.payment.api.dto;

import com.yem.hlm.backend.payment.domain.PaymentTranche;
import com.yem.hlm.backend.payment.domain.TrancheStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TrancheResponse(
        UUID id,
        int trancheOrder,
        String label,
        BigDecimal percentage,
        BigDecimal amount,
        LocalDate dueDate,
        String triggerCondition,
        TrancheStatus status
) {
    public static TrancheResponse from(PaymentTranche t) {
        return new TrancheResponse(
                t.getId(),
                t.getTrancheOrder(),
                t.getLabel(),
                t.getPercentage(),
                t.getAmount(),
                t.getDueDate(),
                t.getTriggerCondition(),
                t.getStatus()
        );
    }
}
