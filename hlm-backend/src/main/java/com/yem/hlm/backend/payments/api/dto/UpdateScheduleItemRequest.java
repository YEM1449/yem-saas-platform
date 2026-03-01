package com.yem.hlm.backend.payments.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateScheduleItemRequest(

        @Size(max = 200)
        String label,

        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        LocalDate dueDate,

        @Size(max = 2000)
        String notes
) {}
