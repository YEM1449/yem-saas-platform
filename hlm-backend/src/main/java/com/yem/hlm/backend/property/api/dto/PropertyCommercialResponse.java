package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.legal.TvaCalculator;
import com.yem.hlm.backend.property.domain.Property;

import java.math.BigDecimal;
import java.util.UUID;

/** VEFA commercial view of a property. {@code prixTtc} is computed, never stored. */
public record PropertyCommercialResponse(
        UUID propertyId,
        Integer etage,
        String orientation,
        String vue,
        BigDecimal surfaceHabitable,
        BigDecimal surfaceTerrasse,
        BigDecimal surfaceCave,
        BigDecimal surfaceParking,
        boolean parkingInclus,
        boolean caveIncluse,
        BigDecimal prixHt,
        BigDecimal tvaTaux,
        BigDecimal prixTtc,
        BigDecimal penaliteRetardJournalier,
        BigDecimal chargesCoproMensuelles,
        String planAppartementKey
) {
    public static PropertyCommercialResponse from(Property p) {
        return new PropertyCommercialResponse(
                p.getId(), p.getFloorNumber(), p.getOrientation(), p.getVue(),
                p.getSurfaceAreaSqm(), p.getSurfaceTerrasse(), p.getSurfaceCave(), p.getSurfaceParking(),
                p.isParkingInclus(), p.isCaveIncluse(),
                p.getPrixHt(), p.getTvaTaux(), TvaCalculator.computePrixTtc(p.getPrixHt(), p.getTvaTaux()),
                p.getPenaliteRetardJournalier(), p.getChargesCoproMensuelles(), p.getPlanAppartementKey());
    }
}
