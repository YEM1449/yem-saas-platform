package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.EcheanceStatut;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Request body for {@code PATCH /api/ventes/{id}/echeances/{eid}/statut}. */
public record UpdateEcheanceStatutRequest(
        @NotNull EcheanceStatut statut,
        /** Date the payment was received (required when statut = PAYEE). */
        LocalDate datePaiement
) {}
