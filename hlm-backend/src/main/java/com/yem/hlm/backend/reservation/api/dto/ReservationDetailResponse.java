package com.yem.hlm.backend.reservation.api.dto;

import com.yem.hlm.backend.reservation.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enriched reservation detail — includes contact and property summaries
 * so the frontend can render the detail page without extra API calls.
 */
public record ReservationDetailResponse(
        UUID id,
        UUID societeId,
        String reservationRef,
        ReservationStatus status,
        LocalDate reservationDate,
        LocalDateTime expiryDate,
        BigDecimal reservationPrice,
        String notes,
        UUID convertedDepositId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        // Contact summary
        UUID contactId,
        String contactFullName,
        String contactPhone,
        String contactEmail,

        // Property summary
        UUID propertyId,
        String propertyTitle,
        String propertyReferenceCode,
        BigDecimal propertyPrice,
        String projectNom,
        String trancheNom,
        String immeubleNom,

        // Linked vente (if exists)
        UUID linkedVenteId
) {}
