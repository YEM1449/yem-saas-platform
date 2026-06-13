package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.StatutReserve;

import java.time.LocalDate;
import java.util.UUID;

/** Project-level view of a delivery reserve — includes vente and property context (A-003). */
public record ReserveLivraisonProjectResponse(
        UUID id,
        String description,
        StatutReserve statut,
        LocalDate dateConstat,
        LocalDate dateLeveePrevue,
        LocalDate dateLeveeReelle,
        UUID responsableUserId,
        UUID venteId,
        String venteRef,
        UUID propertyId,
        String propertyRef
) {}
