package com.yem.hlm.backend.payment.api.dto;

import com.yem.hlm.backend.payment.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPaymentRequest(
        @NotNull @DecimalMin("0.01")
        BigDecimal amountReceived,

        @NotNull
        LocalDate receivedAt,

        @NotNull
        PaymentMethod method,

        @Size(max = 100)
        String reference,

        @Size(max = 2000)
        String notes
) {}
