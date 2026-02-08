package com.yem.hlm.backend.contact.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Backward-compatible request for /api/contacts/{id}/convert-to-client.
 *
 * New meaning (2026-02): create a RESERVATION (Deposit) for a property.
 * The contact becomes TEMP_CLIENT until dueDate (default now + 7 days).
 */
public record ConvertToClientRequest(
        @NotNull
        UUID propertyId,

        @NotNull
        @Positive
        BigDecimal amount,

        /** Optional: default = today */
        LocalDate depositDate,

        @Size(max = 50)
        String reference,

        @Size(min = 3, max = 3)
        String currency,

        /** Optional: default = now + 7 days */
        LocalDateTime dueDate
) {
    public ConvertToClientRequest {
        reference = normalizeTrim(reference);
        currency = normalizeUpper(currency);
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
