package com.yem.hlm.backend.vente.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Request body for {@code POST /api/ventes/{id}/confirm-reservation}. */
public record ConfirmReservationRequest(
        /** Security deposit paid at signature. Validated ≤ 5% of price (Art. 618-4 Loi 44-00). */
        @PositiveOrZero BigDecimal montantDepot
) {}
