package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.MoyenRemboursement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request bodies for the refund endpoints (#028). */
public final class RemboursementRequests {

    private RemboursementRequests() {}

    /** Create/adjust the amount due. */
    public record UpsertRemboursementRequest(
            @NotNull @PositiveOrZero BigDecimal montant,
            @Size(max = 200) String motif
    ) {}

    /** Mark the refund as paid. */
    public record MarquerEffectueRequest(
            @NotNull MoyenRemboursement moyen,
            LocalDate dateRemboursement,
            @Size(max = 100) String reference
    ) {}
}
