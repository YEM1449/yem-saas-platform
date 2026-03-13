package com.yem.hlm.backend.reservation.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request body for {@code POST /api/reservations}.
 */
public record CreateReservationRequest(
        @NotNull UUID contactId,
        @NotNull UUID propertyId,
        /** Optional indicative price — no financial commitment at this stage. */
        BigDecimal reservationPrice,
        /** When the reservation expires if not converted. Defaults to 7 days. */
        @Future LocalDateTime expiryDate,
        String notes
) {}
