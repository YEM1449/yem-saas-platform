package com.yem.hlm.backend.payment.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a payment schedule for a contract.
 */
public record CreatePaymentScheduleRequest(
        @NotEmpty @Valid
        List<TrancheRequest> tranches,

        @Size(max = 2000)
        String notes
) {}
