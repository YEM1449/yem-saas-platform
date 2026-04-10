package com.yem.hlm.backend.reservation.api.dto;

import com.yem.hlm.backend.reservation.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID societeId,
        String reservationRef,
        UUID contactId,
        UUID propertyId,
        UUID reservedByUserId,
        BigDecimal reservationPrice,
        LocalDate reservationDate,
        LocalDateTime expiryDate,
        ReservationStatus status,
        String notes,
        UUID convertedDepositId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
