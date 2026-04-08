package com.yem.hlm.backend.tranche.api.dto;

import com.yem.hlm.backend.tranche.domain.Tranche;
import com.yem.hlm.backend.tranche.domain.TrancheStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only DTO for a Tranche, with optional KPI aggregates.
 */
public record TrancheDto(
        UUID id,
        UUID projectId,
        Integer numero,
        String nom,
        String displayNom,
        TrancheStatut statut,
        LocalDate dateLivraisonPrevue,
        LocalDate dateLivraisonEff,
        LocalDate dateDebutTravaux,
        String permisConstruireRef,
        String description,
        // KPIs (populated by TrancheService.getKpis)
        int buildingsCount,
        int unitsCount,
        int unitesDisponibles,
        int unitesReservees,
        int unitesVendues,
        BigDecimal tauxCommercialisation
) {
    public static TrancheDto from(Tranche t) {
        return new TrancheDto(
                t.getId(), t.getProjectId(),
                t.getNumero(), t.getNom(), t.getDisplayNom(),
                t.getStatut(),
                t.getDateLivraisonPrevue(), t.getDateLivraisonEff(),
                t.getDateDebutTravaux(), t.getPermisConstruireRef(),
                t.getDescription(),
                0, 0, 0, 0, 0, BigDecimal.ZERO
        );
    }
}
