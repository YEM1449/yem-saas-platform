package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import com.yem.hlm.backend.vente.domain.TypeFinancement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Create/update body for a vente's financing file (PUT /api/ventes/{id}/dossier-financement). */
public record UpsertDossierFinancementRequest(
        TypeFinancement typeFinancement,
        String banque,
        BigDecimal montantCredit,
        BigDecimal tauxInteret,
        Integer dureeMois,
        BigDecimal apportPersonnel,
        StatutDossierFinancement statut,
        LocalDate dateDemande,
        LocalDate dateAccord,
        LocalDate dateExpirationAccord,
        String commentaire
) {}
