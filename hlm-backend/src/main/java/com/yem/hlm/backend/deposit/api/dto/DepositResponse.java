package com.yem.hlm.backend.deposit.api.dto;

import com.yem.hlm.backend.deposit.domain.DepositStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DepositResponse(
        UUID id,
        UUID contactId,
        UUID propertyId,
        UUID agentId,
        BigDecimal amount,
        String currency,
        LocalDate depositDate,
        String reference,
        DepositStatus status,
        String notes,
        LocalDateTime dueDate,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
