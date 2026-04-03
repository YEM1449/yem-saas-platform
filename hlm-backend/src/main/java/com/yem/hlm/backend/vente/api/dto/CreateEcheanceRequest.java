package com.yem.hlm.backend.vente.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request body for {@code POST /api/ventes/{id}/echeances}. */
public record CreateEcheanceRequest(
        @NotBlank String libelle,
        @NotNull @Positive BigDecimal montant,
        @NotNull LocalDate dateEcheance,
        String notes
) {}
