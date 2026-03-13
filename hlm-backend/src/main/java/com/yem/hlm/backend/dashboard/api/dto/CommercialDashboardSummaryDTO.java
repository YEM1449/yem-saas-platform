package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full commercial dashboard summary — returned by a single backend call.
 *
 * <h3>Query budget</h3>
 * Up to 16 aggregate queries per request (no entity hydration):
 * <ol>
 *   <li>Sales totals (count, sum, avg agreedPrice)</li>
 *   <li>Deposit totals in period (count, sum amount, filtered by confirmedAt)</li>
 *   <li>Active reservations snapshot (count, sum, avg age days — NOT date-filtered)</li>
 *   <li>Sales by project — top 10</li>
 *   <li>Sales by agent — top 10</li>
 *   <li>Inventory by property status (current state)</li>
 *   <li>Inventory by property type (current state)</li>
 *   <li>Sales amount by day (trend)</li>
 *   <li>Deposits amount by day (trend)</li>
 *   <li>Contract cycle-time pairs (signedAt, confirmedAt) for avgDaysDepositToSale</li>
 *   <li>Active prospects count (PROSPECT + QUALIFIED_PROSPECT contacts)</li>
 *   <li>Discount totals (avgDiscountPercent, maxDiscountPercent) — F3.2</li>
 *   <li>Discount by agent — top 10 — F3.2</li>
 *   <li>Prospect source funnel (prospectsBySource) — F3.4</li>
 *   <li>Active property holds count (property_reservation ACTIVE)</li>
 *   <li>Property holds expiring within 48 h</li>
 * </ol>
 *
 * <h3>Field notes</h3>
 * <ul>
 *   <li>{@code inventoryByStatus} / {@code inventoryByType} reflect current property state,
 *       optionally filtered by {@code projectId} but NOT by date range.</li>
 *   <li>{@code conversionDepositToSaleRate} = salesCount / depositsCount; null if depositsCount = 0.</li>
 *   <li>{@code avgDaysDepositToSale} = average calendar days from deposit.confirmedAt to contract.signedAt
 *       for contracts that originated from a confirmed deposit (sourceDepositId present); null if none.</li>
 *   <li>{@code activeReservationsCount} / {@code activeReservationsTotalAmount} /
 *       {@code avgReservationAgeDays} — current snapshot of PENDING + CONFIRMED deposits
 *       (not date-filtered); optionally scoped by agentId.</li>
 *   <li>{@code activeProspectsCount} — contacts with status PROSPECT or QUALIFIED_PROSPECT,
 *       tenant-wide (not filtered by date/project/agent).
 *       [OPEN POINT: agent/project scoping if contacts gain a direct agentId FK]</li>
 *   <li>{@code asOf} — server timestamp when this DTO was assembled; use to display data freshness.</li>
 * </ul>
 */
public record CommercialDashboardSummaryDTO(
        // ── Date range applied ──────────────────────────────────────────────
        LocalDateTime from,
        LocalDateTime to,

        // ── Freshness timestamp ─────────────────────────────────────────────
        /** Instant at which this summary was computed (before caching). */
        LocalDateTime asOf,

        // ── Sales totals ────────────────────────────────────────────────────
        long salesCount,
        BigDecimal salesTotalAmount,
        BigDecimal avgSaleValue,

        // ── Deposit totals (period-filtered, CONFIRMED) ─────────────────────
        long depositsCount,
        BigDecimal depositsTotalAmount,

        // ── Active reservations snapshot (current, not date-filtered) ────────
        /** PENDING + CONFIRMED deposits currently open for this tenant (/ agent). */
        long activeReservationsCount,
        BigDecimal activeReservationsTotalAmount,
        /** Average age in days of open reservations; null when none. */
        BigDecimal avgReservationAgeDays,

        // ── Active prospects (tenant-wide, not date-filtered) ────────────────
        /**
         * Contacts with status {@code PROSPECT} or {@code QUALIFIED_PROSPECT}.
         * Tenant-wide; not filtered by date/project/agent.
         * [OPEN POINT: agent/project scoping if contacts gain a direct agentId FK]
         */
        long activeProspectsCount,

        // ── Breakdowns (top 10) ─────────────────────────────────────────────
        List<SalesByProjectRow> salesByProject,
        List<SalesByAgentRow>   salesByAgent,

        // ── Inventory (current state, optional projectId filter) ────────────
        Map<String, Long> inventoryByStatus,
        Map<String, Long> inventoryByType,

        // ── Trend series (daily buckets) ────────────────────────────────────
        List<DailySalesPoint> salesAmountByDay,
        List<DailySalesPoint> depositsAmountByDay,

        // ── Conversion (null when no data) ──────────────────────────────────
        BigDecimal conversionDepositToSaleRate,
        BigDecimal avgDaysDepositToSale,

        // ── Discount analytics (F3.2) — null when no contracts with listPrice ──
        /** AVG((listPrice - agreedPrice) / listPrice * 100) for SIGNED contracts with listPrice. */
        BigDecimal avgDiscountPercent,
        /** MAX of same formula. */
        BigDecimal maxDiscountPercent,
        /** Top 10 agents by average discount %. */
        List<DiscountByAgentRow> discountByAgent,

        // ── Prospect source funnel (F3.4) ───────────────────────────────────
        /** Prospects grouped by source with total + converted counts. */
        List<ProspectSourceRow> prospectsBySource,

        // ── Property holds (property_reservation entity, current snapshot) ───
        /** Count of ACTIVE property_reservation records for this tenant. */
        long propertyHoldsCount,
        /** Count of ACTIVE property_reservation records expiring within 48 h. */
        long propertyHoldsExpiringSoon
) {}
