package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.MotifAnnulation;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Request body for {@code PATCH /api/ventes/{id}/statut}. */
public record UpdateVenteStatutRequest(
        @NotNull VenteStatut statut,
        /** Required when statut == ANNULE; captures the reason for cancellation. */
        MotifAnnulation motifAnnulation,
        /** Optional date to record alongside the transition (e.g. date of notarial act). */
        LocalDate dateTransition,
        /** Optional expected closing date override; auto-estimated when null. */
        LocalDate expectedClosingDate,
        String notes
) {}
