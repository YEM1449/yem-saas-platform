package com.yem.hlm.backend.payments.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddPaymentRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        @NotNull
        LocalDate paidAt,

        @Size(max = 50)
        String channel,

        @Size(max = 200)
        String paymentReference,

        @Size(max = 2000)
        String notes
) {}
