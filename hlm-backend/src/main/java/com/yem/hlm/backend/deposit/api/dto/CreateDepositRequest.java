package com.yem.hlm.backend.deposit.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateDepositRequest(
        @NotNull UUID contactId,
        @NotNull UUID propertyId,
        @NotNull @Positive BigDecimal amount,

        /** Optional: default = today */
        LocalDate depositDate,

        /** Optional: auto-generated if blank */
        @Size(max = 50)
        String reference,

        /** Optional: default = MAD */
        @Size(min = 3, max = 3)
        String currency,

        /** Optional: default = now + 7 days */
        LocalDateTime dueDate,

        @Size(max = 1000)
        String notes
) {
    public CreateDepositRequest {
        reference = normalizeTrim(reference);
        currency = normalizeUpper(currency);
        notes = normalizeTrim(notes);
    }

    private static String normalizeUpper(String value) {
        if (value == null) return null;
        var v = value.trim();
        return v.isEmpty() ? null : v.toUpperCase();
    }

    private static String normalizeTrim(String value) {
        if (value == null) return null;
        var v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
