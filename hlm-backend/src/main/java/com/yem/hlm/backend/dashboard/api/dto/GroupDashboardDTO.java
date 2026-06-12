package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated cross-société view for group owners ("Vue Groupe").
 *
 * <p>Returned by {@code GET /api/groupe/dashboard}. One {@link SocieteRow} per société where
 * the current user holds an <b>ADMIN</b> membership, plus group-level {@link GroupTotals}.
 * Rows are sorted by {@code caConfirme} descending (best-performing société first).
 */
public record GroupDashboardDTO(
        GroupTotals totals,
        List<SocieteRow> societes
) {

    /** Per-société summary. All monetary amounts in MAD (HT, as stored on the vente). */
    public record SocieteRow(
            UUID societeId,
            String nom,
            /* Stock (property counts) */
            long unitsDisponibles,
            long unitsReserves,
            long unitsVendus,
            /** Canonical absorption: SOLD / (ACTIVE + RESERVED + SOLD) × 100; 0 when no stock. */
            double absorptionPct,
            /* Revenue */
            /** Sum of prixVente for ventes at ACTE or beyond (clôture commerciale réalisée). */
            BigDecimal caConfirme,
            /** Sum of prixVente for the pre-ACTE active pipeline (PROSPECT → FINANCEMENT). */
            BigDecimal caEnCours,
            /* Pipeline health */
            long ventesActives,
            /** Ventes stuck in COMPROMIS/FINANCEMENT with no movement for 30+ days. */
            long ventesStallees,
            /* Cash (échéancier) */
            BigDecimal encaisseTotal,
            BigDecimal aEncaisser,
            BigDecimal enRetardMontant,
            long enRetardCount,
            /* VEFA alerts */
            long optionsActives,
            long retractationsEnCours
    ) {}

    /** Group-level aggregates across all rows. */
    public record GroupTotals(
            long societesCount,
            long unitsDisponibles,
            long unitsReserves,
            long unitsVendus,
            double absorptionPct,
            BigDecimal caConfirme,
            BigDecimal caEnCours,
            long ventesActives,
            long ventesStallees,
            BigDecimal encaisseTotal,
            BigDecimal aEncaisser,
            BigDecimal enRetardMontant,
            long enRetardCount,
            long optionsActives,
            long retractationsEnCours
    ) {}
}
