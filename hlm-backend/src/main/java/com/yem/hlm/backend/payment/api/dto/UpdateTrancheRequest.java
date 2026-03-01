package com.yem.hlm.backend.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Patch request for a single tranche. All fields are optional;
 * only PLANNED tranches may be updated.
 */
public record UpdateTrancheRequest(
        @Size(max = 200)
        String label,

        @DecimalMin("0.01")
        BigDecimal amount,

        LocalDate dueDate,

        @Size(max = 500)
        String triggerCondition
) {}
