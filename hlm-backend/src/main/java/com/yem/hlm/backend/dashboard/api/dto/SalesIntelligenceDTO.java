package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Production-grade sales intelligence snapshot — investor-grade BI layer.
 *
 * <p>Covers four analytical domains:
 * <ul>
 *   <li>Sales breakdown by property type (units, CA, avg ticket, price/m²)</li>
 *   <li>Time-to-close distribution (sales cycle health)</li>
 *   <li>Inventory value analytics (portfolio, unsold stock, aging)</li>
 *   <li>Price per sqm by type and project (asset pricing intelligence)</li>
 * </ul>
 */
public record SalesIntelligenceDTO(

        LocalDateTime asOf,

        // ── Inventory value ────────────────────────────────────────────────────
        /** SUM(price) for ACTIVE + RESERVED properties — unsold committed stock. */
        BigDecimal unsoldInventoryValue,
        /** SUM(price) for all non-DRAFT properties — total portfolio valuation. */
        BigDecimal totalPortfolioValue,
        /** Count of ACTIVE (available) units. */
        long activeUnitsCount,
        /** Count of RESERVED units. */
        long reservedUnitsCount,
        /** AVG(price) for ACTIVE units — average listing price. */
        BigDecimal avgListPriceActive,

        // ── Sales breakdown by property type ──────────────────────────────────
        List<SalesByTypeRow> salesByType,

        // ── Time-to-close distribution ─────────────────────────────────────────
        /** Average days from vente creation to LIVRE for all closed deals. */
        Double avgDaysToClose,
        List<TimeToCloseRow> timeToCloseBuckets,

        // ── Inventory aging ────────────────────────────────────────────────────
        List<InventoryAgingRow> inventoryAging,

        // ── Price per sqm ──────────────────────────────────────────────────────
        /** Overall avg price per sqm across all ventes with surface data. */
        Double globalAvgPricePerSqm,
        List<PricePerSqmRow> pricePerSqmByType,
        List<PricePerSqmProjectRow> pricePerSqmByProject

) {

    public record SalesByTypeRow(
            String propertyType,
            long ventesCount,
            BigDecimal totalCA,
            BigDecimal avgPrix,
            Double avgSurfaceSqm,
            Double avgPricePerSqm
    ) {}

    public record TimeToCloseRow(
            /** LT_30 | D30_60 | D61_90 | D91_180 | GT_180 */
            String bucket,
            String bucketLabel,
            long count,
            Double avgDays
    ) {}

    public record InventoryAgingRow(
            /** FRESH | SHORT | MEDIUM | LONG | STALE */
            String bucket,
            String bucketLabel,
            long count,
            BigDecimal totalValue
    ) {}

    public record PricePerSqmRow(
            String propertyType,
            Double avgPricePerSqm,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            long count
    ) {}

    public record PricePerSqmProjectRow(
            String projectId,
            String projectName,
            Double avgPricePerSqm,
            long sampleSize
    ) {}
}
