package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Role-aware home dashboard snapshot.
 *
 * <p>The payload is identical in structure for all roles; the data content is scoped:
 * <ul>
 *   <li>ADMIN / MANAGER — full société scope, all agents.</li>
 *   <li>AGENT — personal scope: only their own ventes/tasks.</li>
 * </ul>
 *
 * <p>Designed for fast load (≤ 10 aggregate queries, cached 30 s).
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

        // ── Tâches ────────────────────────────────────────────────────────────
        long openTasksCount,
        long overdueTasksCount,
        long tasksDueTodayCount,

        // ── Widgets ───────────────────────────────────────────────────────────
        /** Up to 5 recent ventes for the widget. */
        List<RecentVenteRow> recentVentes,
        /** Up to 8 urgent tasks (overdue or due today). */
        List<UrgentTaskRow> urgentTasks

) {

    public record RecentVenteRow(
            java.util.UUID id,
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
}
