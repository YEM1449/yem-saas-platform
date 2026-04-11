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
}
