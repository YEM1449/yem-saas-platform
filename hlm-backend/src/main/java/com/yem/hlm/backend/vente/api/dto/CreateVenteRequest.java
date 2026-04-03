package com.yem.hlm.backend.vente.api.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/ventes}.
 *
 * <p>A vente can be created directly or from a reservation
 * (supply reservationId to auto-populate contact/property/agent).
 */
public record CreateVenteRequest(
        /** Existing reservation to convert (mutually exclusive with contactId/propertyId). */
        UUID reservationId,
        /** Required when not converting a reservation. */
        UUID contactId,
        /** Required when not converting a reservation. */
        UUID propertyId,
        /** Defaults to the current user when null. */
        UUID agentId,
        @Positive BigDecimal prixVente,
        LocalDate dateCompromis,
        LocalDate dateLivraisonPrevue,
        String notes
) {}
