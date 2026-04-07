package com.yem.hlm.backend.tranche.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response returned after a successful project generation.
 */
public record ProjectGenerationResponse(
        UUID projectId,
        String projectNom,
        int tranchesGenerated,
        int buildingsGenerated,
        int unitsGenerated,
        List<TrancheGenerationSummary> tranches,
        Map<String, Integer> unitsByType,
        BigDecimal valeurTotale,
        String status,
        String message
) {

    public record TrancheGenerationSummary(
            UUID trancheId,
            String displayNom,
            LocalDate dateLivraisonPrevue,
            int buildingsCount,
            int unitsCount,
            List<BuildingSummary> buildings
    ) {}

    public record BuildingSummary(
            UUID immeubleId,
            String nom,
            int floorCount,
            int unitsCount
    ) {}
}
