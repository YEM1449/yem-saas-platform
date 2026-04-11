package com.yem.hlm.backend.reservation.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prefill data returned by {@code GET /api/reservations/{id}/vente-prefill}.
 * Lets the frontend pre-populate the "Create Vente" form with locked contact
 * and property information derived from the reservation.
 */
public record VentePrefillResponse(
        UUID reservationId,
        String reservationRef,
        BigDecimal reservationPrice,

        // Contact (read-only in form)
        UUID contactId,
        String contactFullName,

        // Property (read-only in form)
        UUID propertyId,
        String propertyTitle,
        String propertyReferenceCode,
        BigDecimal propertyPrice,
        String projectNom,
        String trancheNom,
        String immeubleNom,

        /** Suggested sale price = propertyPrice − reservationPrice. */
        BigDecimal suggestedPrixVente
) {}
