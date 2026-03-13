package com.yem.hlm.backend.reservation.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Request body for {@code POST /api/reservations/{id}/convert-to-deposit}.
 * Converts an ACTIVE reservation into a formal Deposit with a financial commitment.
 */
public record ConvertReservationToDepositRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        LocalDate depositDate,
        String reference,
        /** Deposit due date. Defaults to 7 days from now. */
        LocalDateTime dueDate
) {}
