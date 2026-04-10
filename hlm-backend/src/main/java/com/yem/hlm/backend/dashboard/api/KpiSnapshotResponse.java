package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.domain.KpiSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record KpiSnapshotResponse(
        UUID trancheId,
        BigDecimal tauxCommercialisation,
        BigDecimal montantEncaisse,
        BigDecimal tauxRecouvrement,
        BigDecimal soldeRestant,
        Integer delaiMoyenVenteJours,
        LocalDateTime computedAt
) {
    public static KpiSnapshotResponse from(KpiSnapshot s) {
        return new KpiSnapshotResponse(
                s.getTrancheId(),
                s.getTauxCommercialisation(),
                s.getMontantEncaisse(),
                s.getTauxRecouvrement(),
                s.getSoldeRestant(),
                s.getDelaiMoyenVenteJours(),
                s.getComputedAt()
        );
    }
}
