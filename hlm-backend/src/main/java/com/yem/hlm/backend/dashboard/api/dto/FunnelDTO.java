package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sales funnel snapshot — counts at each commercial stage with conversion
 * and drop-off ratios computed against the previous (upstream) stage.
 *
 * <p>Stages currently tracked (visit tracking is not yet wired):
 * <ol>
 *   <li>{@code PROSPECTS} — contacts in {@code PROSPECT} status</li>
 *   <li>{@code QUALIFIED} — contacts in {@code QUALIFIED_PROSPECT} status</li>
 *   <li>{@code RESERVATIONS} — active reservations (status {@code ACTIVE})</li>
 *   <li>{@code VENTES} — non-terminal ventes (active pipeline)</li>
 *   <li>{@code LIVRE} — finalised ventes (status {@code LIVRE})</li>
 * </ol>
 */
public record FunnelDTO(

        LocalDateTime asOf,
        List<FunnelStage> stages

) {

    /**
     * @param stage           machine code (see class javadoc)
     * @param label           localised label for the UI
     * @param count           absolute number of items currently at this stage
     * @param conversionRate  {@code count / previousStage.count * 100} (1-decimal),
     *                        {@code 100} on the first stage, {@code null} when upstream is zero
     * @param dropOffRate     {@code 100 - conversionRate}, same null semantics
     */
    public record FunnelStage(
            String stage,
            String label,
            long count,
            BigDecimal conversionRate,
            BigDecimal dropOffRate
    ) {}
}
