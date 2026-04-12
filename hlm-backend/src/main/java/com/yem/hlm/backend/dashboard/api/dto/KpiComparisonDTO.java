package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Period-over-period comparison for the executive cockpit.
 *
 * <p>Each {@link KpiDelta} carries current value, previous value, raw delta and
 * percentage change so the frontend can render coloured chips and arrows without
 * any client-side maths.
 *
 * <p>Sparklines hold a chronological list of weekly buckets (12 weeks) suitable
 * for an inline SVG mini-chart.
 */
public record KpiComparisonDTO(

        LocalDateTime asOf,

        /** CA signé (non-ANNULE) — current calendar month vs previous calendar month. */
        KpiDelta caSigne,

        /** Ventes créées — last 30 days vs the 30 days before that. */
        KpiDelta ventesCreated,

        /** Réservations créées — last 30 days vs the 30 days before that. */
        KpiDelta reservations,

        /** Encaissements (échéances payées) — current month vs previous month. */
        KpiDelta encaisse,

        /** 12 weekly buckets of CA signé (chronological, oldest first). */
        List<SparklinePoint> caSparkline,

        /** 12 weekly buckets of vente creation count. */
        List<SparklinePoint> ventesSparkline

) {

    /**
     * One KPI's current/previous comparison.
     *
     * @param current   value for the current period
     * @param previous  value for the previous comparable period
     * @param delta     {@code current - previous}
     * @param deltaPct  percentage change with 1-decimal precision; {@code null} when previous is zero
     */
    public record KpiDelta(
            BigDecimal current,
            BigDecimal previous,
            BigDecimal delta,
            BigDecimal deltaPct
    ) {}

    /**
     * One bucket of a sparkline series.
     *
     * @param weekStart Monday of the ISO week
     * @param value     aggregated value for that week
     */
    public record SparklinePoint(
            LocalDate weekStart,
            BigDecimal value
    ) {}
}
