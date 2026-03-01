package com.yem.hlm.backend.payment.api.dto;

import com.yem.hlm.backend.payment.domain.PaymentCall;
import com.yem.hlm.backend.payment.domain.PaymentCallStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCallResponse(
        UUID id,
        UUID trancheId,
        int callNumber,
        BigDecimal amountDue,
        LocalDateTime issuedAt,
        LocalDate dueDate,
        PaymentCallStatus status
) {
    public static PaymentCallResponse from(PaymentCall pc) {
        return new PaymentCallResponse(
                pc.getId(),
                pc.getTranche().getId(),
                pc.getCallNumber(),
                pc.getAmountDue(),
                pc.getIssuedAt(),
                pc.getDueDate(),
                pc.getStatus()
        );
    }
}
