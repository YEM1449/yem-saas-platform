package com.yem.hlm.backend.property.api.dto;

import java.math.BigDecimal;

/**
 * VEFA commercial update for a property (PATCH /api/properties/{id}/commercial). All fields
 * nullable. When {@code tvaTaux} is null but {@code prixHt} is set, the legal rate is suggested
 * from surface + price ({@code logementSocial} for the 0% social bracket). Isolated from the
 * core property DTOs so their many call-sites stay stable.
 */
public record UpdatePropertyCommercialRequest(
        BigDecimal prixHt,
        BigDecimal tvaTaux,
        Boolean logementSocial,
        String vue,
        BigDecimal surfaceTerrasse,
        BigDecimal surfaceCave,
        BigDecimal surfaceParking,
        Boolean parkingInclus,
        Boolean caveIncluse,
        BigDecimal penaliteRetardJournalier,
        BigDecimal chargesCoproMensuelles
) {}
