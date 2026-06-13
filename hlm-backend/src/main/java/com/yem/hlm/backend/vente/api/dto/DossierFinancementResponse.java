package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.DossierFinancement;
import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import com.yem.hlm.backend.vente.domain.TypeFinancement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DossierFinancementResponse(
        UUID id,
        UUID venteId,
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
        String commentaire,
        LocalDateTime updatedAt
) {
    public static DossierFinancementResponse from(DossierFinancement d) {
        return new DossierFinancementResponse(
                d.getId(), d.getVenteId(), d.getTypeFinancement(), d.getBanque(),
                d.getMontantCredit(), d.getTauxInteret(), d.getDureeMois(), d.getApportPersonnel(),
                d.getStatut(), d.getDateDemande(), d.getDateAccord(), d.getDateExpirationAccord(),
                d.getCommentaire(), d.getUpdatedAt());
    }
}
