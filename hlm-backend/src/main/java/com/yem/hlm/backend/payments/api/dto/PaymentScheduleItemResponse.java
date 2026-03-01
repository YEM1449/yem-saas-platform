package com.yem.hlm.backend.payments.api.dto;

import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentScheduleItemResponse(
        UUID id,
        UUID contractId,
        UUID projectId,
        UUID propertyId,
        int sequence,
        String label,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal amountRemaining,
        LocalDate dueDate,
        PaymentScheduleStatus status,
        LocalDateTime issuedAt,
        LocalDateTime sentAt,
        LocalDateTime canceledAt,
        String notes,
        LocalDateTime createdAt
) {
    public static PaymentScheduleItemResponse from(PaymentScheduleItem item,
                                                    BigDecimal amountPaid,
                                                    BigDecimal amountRemaining) {
        return new PaymentScheduleItemResponse(
                item.getId(),
                item.getContractId(),
                item.getProjectId(),
                item.getPropertyId(),
                item.getSequence(),
                item.getLabel(),
                item.getAmount(),
                amountPaid,
                amountRemaining,
                item.getDueDate(),
                item.getStatus(),
                item.getIssuedAt(),
                item.getSentAt(),
                item.getCanceledAt(),
                item.getNotes(),
                item.getCreatedAt()
        );
    }
}
