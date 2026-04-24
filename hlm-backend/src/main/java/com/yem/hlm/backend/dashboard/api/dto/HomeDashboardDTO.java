package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Role-aware home dashboard snapshot — enriched for real-estate consultants.
 *
 * <p>The payload is identical in structure for all roles; the data content is scoped:
 * <ul>
 *   <li>ADMIN / MANAGER — full société scope, all agents.</li>
 *   <li>AGENT — personal scope: only their own ventes/tasks.</li>
 * </ul>
 *
 * <p>Designed for fast load (≤ 16 aggregate queries, cached 30 s).
 */
public record HomeDashboardDTO(

        LocalDateTime asOf,

        // ── Pipeline: ventes actives ──────────────────────────────────────────
        /** Active ventes (all non-terminal: COMPROMIS, FINANCEMENT, ACTE_NOTARIE). */
        long activeVentesCount,
        /** Sum of prixVente for active pipeline. */
        BigDecimal caActivePipeline,
        /** Pipeline breakdown: statut → count. */
        Map<String, Long> ventesParStatut,

        // ── CA mensuel (trend indicator) ──────────────────────────────────────
        /** Sum of prixVente for ventes created this calendar month (non-ANNULE). */
        BigDecimal caSigneMoisCourant,
        /** Sum of prixVente for ventes created last calendar month (non-ANNULE). */
        BigDecimal caSigneMoisPrecedent,
        /** Total CA from LIVRE (delivered) ventes — realized revenue. */
        BigDecimal caLivre,

        // ── Écheancier pulse ──────────────────────────────────────────────────
        /** Sum of unpaid échéances due in the next 30 days. */
        BigDecimal echeancesA30JoursMontant,
        /** Sum of unpaid échéances whose due date has passed. */
        BigDecimal echeancesEnRetardMontant,
        /** Count of overdue unpaid échéances. */
        long echeancesEnRetardCount,

        // ── Inventory snapshot ────────────────────────────────────────────────
        long biensDraftCount,
        long biensActifsCount,
        long biensReservesCount,
        long biensVendusCount,
        /**
         * Taux d'absorption = sold / (sold + active + reserved) * 100.
         * Key real-estate metric: % of launchable stock that is sold.
         * Null when no marketable stock.
         */
        BigDecimal tauxAbsorption,
        /** biensActifs + biensReserves + biensVendus — stock commercialisé. */
        long stockCommercialise,

        // ── Pipeline entrée: prospects & réservations ─────────────────────────
        long activeProspectsCount,
        long activeReservationsCount,
        long reservationsExpirantBientot,

        // ── Alertes opérationnelles ───────────────────────────────────────────
        /** Ventes stuck in COMPROMIS or FINANCEMENT for more than 30 days. */
        long ventesStalleesCount,

        // ── Tâches ────────────────────────────────────────────────────────────
        long openTasksCount,
        long overdueTasksCount,
        long tasksDueTodayCount,

        // ── Owner KPIs (real-estate-specific) ─────────────────────────────────
        /**
         * Cancellation rate over the last 90 days = ANNULE / total ventes (created),
         * expressed as a percentage with 1-decimal precision. Null if no ventes.
         */
        BigDecimal cancellationRate90d,
        /** Average ticket = AVG(prixVente) over LIVRE ventes. */
        BigDecimal avgTicketLivre,
        /**
         * Conversion rate over the last 30 days = ventes created / reservations created,
         * percentage with 1 decimal. Null if no reservations.
         */
        BigDecimal conversionRate30d,
        /** Sum of PAID échéances whose due date falls in current calendar month. */
        BigDecimal encaisseMoisCourant,
        /** Top 5 agents by signed CA (last 90 days). Empty for AGENT role. */
        List<AgentLeaderboardRow> topAgents,

        // ── Executive view KPIs (Wave 13) ─────────────────────────────────────
        /** Sum of prixVente (non-ANNULE) for ventes created since Jan 1st of the current year. */
        BigDecimal caYtd,
        /** Sum of prixVente (non-ANNULE) for ventes created in the same calendar month one year ago. */
        BigDecimal caSameMonthLastYear,
        /**
         * Year-over-year percent change = (caSigneMoisCourant − caSameMonthLastYear) / caSameMonthLastYear × 100.
         * Null when caSameMonthLastYear is 0.
         */
        BigDecimal caYoYPct,
        /**
         * Months of supply = biensActifs / (ventes last 90d / 3).
         * Classic absorption horizon — how many months of active stock remain at the current sales pace.
         * Null when no sales in 90d.
         */
        BigDecimal monthsOfSupply,
        /**
         * Rolling 4-week velocity: count of ventes created in the last 28 days divided by 4.
         * Expressed in ventes per week (1 decimal).
         */
        BigDecimal salesVelocityPerWeek,
        /**
         * Win rate over the last 90 days = LIVRE / (LIVRE + ANNULE) × 100, on ventes created in window.
         * Null when no terminal ventes in window.
         */
        BigDecimal winRate90d,
        /**
         * Days Sales Outstanding (approximation): overdue unpaid amount divided by daily paid run-rate
         * of the trailing 90 days. Null when no paid receivables in window.
         */
        BigDecimal dsoRolling90d,
        /**
         * Collection efficiency (90d trailing) = paid ÷ due for échéances whose dateEcheance is in
         * [today-90d, today]. Null when nothing was due.
         */
        BigDecimal collectionEfficiency90d,
        /** Monthly CA target from Societe.caMensuelCible; null if not configured. */
        BigDecimal caMensuelCible,
        /** Monthly vente-count target from Societe.ventesMensuelCible; null if not configured. */
        Long ventesMensuelCible,
        /**
         * Quota attainment MTD % = caSigneMoisCourant / caMensuelCible × 100.
         * Null when target not configured or 0.
         */
        BigDecimal quotaAttainmentMtdPct,
        /** Count of non-ANNULE ventes created in the current calendar month. */
        Long ventesSigneesMoisCourantCount,
        /**
         * Ventes-count pacing % = ventesSigneesMoisCourantCount / ventesMensuelCible × 100.
         * Null when target not configured or 0.
         */
        BigDecimal quotaVentesAttainmentMtdPct,
        /** Up to 10 upcoming tranche deliveries within the next 90 days. */
        List<UpcomingDeliveryRow> upcomingDeliveries,

        // ── Inventory by type (quantitative breakdown) ───────────────────────
        /** Per-type counts (active/reserved/sold/draft/total) with absorption rate. */
        List<InventoryTypeRow> inventoryByType,

        // ── Pipeline stage aging ──────────────────────────────────────────────
        /** Aging metrics per active vente statut (COMPROMIS/FINANCEMENT/ACTE_NOTARIE). Empty for AGENT. */
        List<PipelineStageAgingRow> pipelineStageAging,

        // ── Per-type velocity ─────────────────────────────────────────────────
        /** Avg days-to-close + avg ticket per property type (LIVRE ventes only). Empty for AGENT. */
        List<TypeVelocityRow> typeVelocity,

        // ── Trend & project breakdown ─────────────────────────────────────────
        /** Monthly CA signed for the last 6 months (oldest → newest). Empty for AGENT. */
        List<MonthlyTrendPoint> monthlyTrend,
        /** Top 8 projects by total CA signed (all time, non-ANNULE). Empty for AGENT. */
        List<ProjectBreakdownRow> projectBreakdown,
        /** CA and vente count per tranche (via property.tranche_id). Empty for AGENT. */
        List<TrancheBreakdownRow> trancheBreakdown,
        /** CA and vente count per immeuble (via property.immeuble_id). Empty for AGENT. */
        List<ImmeubleBreakdownRow> immeubleBreakdown,

        // ── Widgets ───────────────────────────────────────────────────────────
        /** Up to 5 recent ventes for the widget. */
        List<RecentVenteRow> recentVentes,
        /** Up to 8 urgent tasks (overdue or due today). */
        List<UrgentTaskRow> urgentTasks

) {

    public record RecentVenteRow(
            java.util.UUID id,
            String venteRef,
            String contactFullName,
            String statut,
            BigDecimal prixVente,
            LocalDateTime createdAt
    ) {}

    public record UrgentTaskRow(
            java.util.UUID id,
            String title,
            String status,
            LocalDateTime dueDate,
            /** null if no linked contact */
            java.util.UUID contactId
    ) {}

    public record AgentLeaderboardRow(
            java.util.UUID agentId,
            String agentName,
            BigDecimal totalCA,
            long ventesCount
    ) {}

    public record MonthlyTrendPoint(
            /** "YYYY-MM" — e.g. "2026-03" */
            String yearMonth,
            /** Short label — e.g. "Mar 26" */
            String label,
            BigDecimal caSigne
    ) {}

    public record ProjectBreakdownRow(
            String projectId,
            String projectName,
            BigDecimal totalCA,
            long ventesCount
    ) {}

    public record UpcomingDeliveryRow(
            java.util.UUID trancheId,
            String trancheLabel,
            java.util.UUID projectId,
            String projectName,
            java.time.LocalDate dateLivraisonPrevue,
            long daysUntilDelivery,
            long totalUnits,
            long soldUnits
    ) {}

    public record InventoryTypeRow(
            String type,
            long activeCount,
            long reservedCount,
            long soldCount,
            long draftCount,
            long totalCount,
            /** absorptionRate = soldCount / (activeCount + reservedCount + soldCount) × 100; null when 0 */
            BigDecimal absorptionRate
    ) {}

    public record TrancheBreakdownRow(
            String trancheId,
            String trancheLabel,
            String projectId,
            String projectName,
            BigDecimal totalCA,
            long ventesCount
    ) {}

    public record ImmeubleBreakdownRow(
            String immeubleId,
            String immeubleNom,
            String projectId,
            String projectName,
            BigDecimal totalCA,
            long ventesCount
    ) {}

    /**
     * Aging metrics for one active pipeline stage (COMPROMIS / FINANCEMENT / ACTE_NOTARIE).
     * avgDays / maxDays = days since vente.createdAt (proxy for time in pipeline).
     * stalled30dCount = ventes in this stage for more than 30 days.
     */
    public record PipelineStageAgingRow(
            String statut,
            long count,
            double avgDays,
            double maxDays,
            long stalled30dCount,
            BigDecimal totalValue
    ) {}

    /**
     * Per-property-type velocity for LIVRE ventes.
     * avgDaysToClose uses COALESCE(date_livraison_reelle, stage_entry_date) − created_at.
     * null when no LIVRE ventes exist for the type.
     */
    public record TypeVelocityRow(
            String type,
            long soldCount,
            BigDecimal avgPrix,
            Double avgDaysToClose
    ) {}
}
