package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.ReserveLivraison;
import com.yem.hlm.backend.vente.domain.StatutReserve;

import java.time.LocalDate;
import java.util.UUID;

/** Response view of a delivery reserve. */
public record ReserveLivraisonResponse(
        UUID id,
        String description,
        StatutReserve statut,
        LocalDate dateConstat,
        LocalDate dateLeveePrevue,
        LocalDate dateLeveeReelle,
        UUID responsableUserId
) {
    public static ReserveLivraisonResponse from(ReserveLivraison r) {
        return new ReserveLivraisonResponse(
                r.getId(), r.getDescription(), r.getStatut(),
                r.getDateConstat(), r.getDateLeveePrevue(), r.getDateLeveeReelle(),
                r.getResponsableUserId());
    }
}
