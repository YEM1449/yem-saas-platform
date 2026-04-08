package com.yem.hlm.backend.tranche.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Generation config for one Tranche within a project.
 *
 * @param numero               sequential number (1, 2, 3…)
 * @param nom                  optional label ("Tranche 1 — Le Jasmin")
 * @param dateLivraisonPrevue  contractual delivery date — REQUIRED
 * @param dateDebutTravaux     construction start date
 * @param permisConstruireRef  building permit reference
 * @param buildings            buildings to generate in this tranche
 */
public record TrancheConfig(
        @NotNull Integer numero,
        String nom,
        @NotNull LocalDate dateLivraisonPrevue,
        LocalDate dateDebutTravaux,
        String permisConstruireRef,
        @NotNull List<BuildingConfig> buildings
) {}
