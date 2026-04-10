package com.yem.hlm.backend.tranche.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Top-level request for the "generate project" wizard endpoint.
 * <p>
 * Creates a Project, one or more Tranches, the Immeubles within each Tranche,
 * and all Property units for each floor — all in a single transaction.
 */
public record ProjectGenerationRequest(

        // ── Project info ───────────────────────────────────────────────────
        @NotBlank @Size(max = 200) String projectNom,
        @Size(max = 2000) String projectDescription,
        @Size(max = 500)  String projectAdresse,
        @NotBlank @Size(max = 100) String projectVille,
        @Size(max = 20)   String projectCodePostal,

        // ── Optional professional fields ───────────────────────────────────
        @Size(max = 200) String maitreOuvrage,
        LocalDate dateOuvertureCommercialisation,
        BigDecimal tvaTaux,
        BigDecimal surfaceTerrainM2,
        BigDecimal prixMoyenM2Cible,

        // ── Building naming (shared across ALL tranches) ───────────────────
        /** LETTRE (A,B,C…) | CHIFFRE (1,2,3…) | CUSTOM */
        String buildingNaming,
        /** "Bâtiment", "Immeuble", "Villa" */
        String buildingPrefix,
        /** BUILDING_FLOOR_UNIT | FLOOR_UNIT | SEQUENTIAL */
        String unitRefPattern,
        String unitPrefix,
        String rdcLabel,
        boolean includeParking,
        String parkingPrefix,
        boolean parkingUnderground,

        // ── Tranches ───────────────────────────────────────────────────────
        @NotEmpty @Valid List<TrancheConfig> tranches
) {
    public ProjectGenerationRequest {
        if (buildingNaming == null || buildingNaming.isBlank()) buildingNaming = "LETTRE";
        if (buildingPrefix == null || buildingPrefix.isBlank()) buildingPrefix = "Bâtiment";
        if (unitRefPattern == null || unitRefPattern.isBlank()) unitRefPattern = "BUILDING_FLOOR_UNIT";
        if (rdcLabel == null || rdcLabel.isBlank()) rdcLabel = "RDC";
        if (parkingPrefix == null || parkingPrefix.isBlank()) parkingPrefix = "P";
    }
}
