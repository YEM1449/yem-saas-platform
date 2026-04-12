package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Revenue forecast engine — weighted pipeline summed by expected closing horizon.
 */
public record ForecastDTO(

        LocalDateTime asOf,

        /** Sum of weighted pipeline (prixVente * probability / 100) where expectedClosingDate <= today + 30d. */
        BigDecimal next30Days,

        /** Same, <= today + 60d. */
        BigDecimal next60Days,

        /** Same, <= today + 90d. */
        BigDecimal next90Days,

        /** Weighted pipeline with no expectedClosingDate (unforecastable). */
        BigDecimal undated,

        /** Count of active deals with no expected closing date (prompt user to fill in). */
        long undatedCount

) {}
