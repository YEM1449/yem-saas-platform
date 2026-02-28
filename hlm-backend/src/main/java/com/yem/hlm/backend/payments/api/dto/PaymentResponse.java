package com.yem.hlm.backend.payments.api.dto;

import com.yem.hlm.backend.payments.domain.SchedulePayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID scheduleItemId,
        BigDecimal amountPaid,
        LocalDateTime paidAt,
        String channel,
        String paymentReference,
        String notes,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(SchedulePayment p) {
        return new PaymentResponse(
                p.getId(),
                p.getScheduleItemId(),
                p.getAmountPaid(),
                p.getPaidAt(),
                p.getChannel(),
                p.getPaymentReference(),
                p.getNotes(),
                p.getCreatedAt()
        );
    }
}
