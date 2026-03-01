package com.yem.hlm.backend.payment.api.dto;

import com.yem.hlm.backend.payment.domain.Payment;
import com.yem.hlm.backend.payment.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID paymentCallId,
        BigDecimal amountReceived,
        LocalDate receivedAt,
        PaymentMethod method,
        String reference,
        String notes,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getPaymentCall().getId(),
                p.getAmountReceived(),
                p.getReceivedAt(),
                p.getMethod(),
                p.getReference(),
                p.getNotes(),
                p.getCreatedAt()
        );
    }
}
