package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.Remboursement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Refund tracking view for a vente (#028). */
public record RemboursementResponse(
        UUID       id,
        UUID       venteId,
        BigDecimal montant,
        String     moyen,
        String     statut,
        String     reference,
        String     motif,
        LocalDate  dateRemboursement
) {
    public static RemboursementResponse from(Remboursement r) {
        return new RemboursementResponse(
                r.getId(), r.getVenteId(), r.getMontant(),
                r.getMoyen() != null ? r.getMoyen().name() : null,
                r.getStatut().name(), r.getReference(), r.getMotif(),
                r.getDateRemboursement());
    }
}
