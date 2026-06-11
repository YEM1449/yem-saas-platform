package com.yem.hlm.backend.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * VEFA treasury dashboard (Wave 12 P6) — cash position from the legal échéancier plus
 * the actionable VEFA alerts (overdue calls, active options, retractions in progress,
 * bank agreements about to expire).
 */
public record TresorerieDashboardDTO(
        BigDecimal encaisseTotal,
        BigDecimal aEncaisser,
        BigDecimal previsionnel6Mois,
        BigDecimal enRetardMontant,
        long       enRetardCount,
        long       optionsActives,
        long       retractationsEnCours,
        long       accordsExpirant15j,
        List<AppelEnRetard> appelsEnRetard
) {
    /** One overdue call-for-funds line. */
    public record AppelEnRetard(
            UUID       venteId,
            String     venteRef,
            String     acquereur,
            String     libelle,
            BigDecimal montant,
            LocalDate  dateEcheance,
            long       joursRetard
    ) {}
}
