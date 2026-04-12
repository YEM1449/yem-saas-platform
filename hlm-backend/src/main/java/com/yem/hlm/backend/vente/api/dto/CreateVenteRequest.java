package com.yem.hlm.backend.vente.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/ventes}.
 *
 * <p><b>Direct creation</b>: supply {@code contactId}, {@code propertyId} and a positive
 * {@code prixVente}.
 *
 * <p><b>From reservation</b>: supply only {@code reservationId}.  The contact, property and
 * agent are derived automatically.  The final price is computed as:
 * <pre>
 *   prixVente = property.price − reservation.reservationPrice − reduction
 * </pre>
 * Pass {@code prixVente} to override the computed value; pass {@code reduction} to apply a
 * commercial discount on top of the advance already paid.
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
        /**
         * Final sale price.
         * <ul>
         *   <li>Direct creation: required and must be positive.</li>
         *   <li>From reservation: optional — computed automatically if null.</li>
         * </ul>
         */
        BigDecimal prixVente,
        /**
         * Optional commercial discount applied on top of the reservation advance.
         * Only meaningful when converting a reservation; ignored for direct creation.
         */
        @PositiveOrZero BigDecimal reduction,
        LocalDate dateCompromis,
        LocalDate dateLivraisonPrevue,
        LocalDate expectedClosingDate,
        String notes
) {}
