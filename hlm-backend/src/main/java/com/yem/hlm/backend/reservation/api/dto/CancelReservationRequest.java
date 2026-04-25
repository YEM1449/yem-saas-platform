package com.yem.hlm.backend.reservation.api.dto;

public record CancelReservationRequest(
        /** Optional free-text reason for the cancellation (for traceability). */
        String raisonAnnulation
) {}
