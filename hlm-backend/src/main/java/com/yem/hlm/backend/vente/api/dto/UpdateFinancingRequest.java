package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.TypeFinancement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request body for {@code PATCH /api/ventes/{id}/financement}. All fields are optional (patch semantics). */
public record UpdateFinancingRequest(
        TypeFinancement typeFinancement,
        BigDecimal montantCredit,
        String banqueCredit,
        Boolean creditObtenu,
        LocalDate dateLimiteConditionCredit,
        String notaireAcquereurNom,
        String notaireAcquereurEmail,
        LocalDate datePvReception,
        LocalDate dateTitreFoncier
) {}
