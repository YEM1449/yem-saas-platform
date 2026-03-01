package com.yem.hlm.backend.payment.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Describes a single tranche within a payment schedule creation request.
 */
public record TrancheRequest(
        @NotBlank @Size(max = 200)
        String label,

        @NotNull @DecimalMin("0.01") @DecimalMax("100.00")
        BigDecimal percentage,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        LocalDate dueDate,

        @Size(max = 500)
        String triggerCondition
) {}
