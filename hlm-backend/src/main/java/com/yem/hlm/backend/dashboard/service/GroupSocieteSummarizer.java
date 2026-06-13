package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO.SocieteRow;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds one {@code GroupDashboardDTO.SocieteRow} for a single société.
 *
 * <p><b>Caller contract (RLS):</b> {@code SocieteContext.setSocieteId(...)} must point at the
 * target société <em>before</em> this method is invoked. {@code RlsContextAspect} reads the
 * context at the start of this method's transaction to set the PostgreSQL RLS session variable;
 * without the switch, row-level security would filter the explicit {@code societeId} queries
 * down to the JWT's société and silently return zeros. {@code GroupDashboardService} owns the
 * switch/restore; do not call this bean from anywhere else.
 */
@Service
public class GroupSocieteSummarizer {

    /** ACTE and beyond = clôture commerciale réalisée (excludes ANNULE by enumeration). */
    private static final List<VenteStatut> CONFIRMED_STATUTS = List.of(
            VenteStatut.ACTE,
            VenteStatut.LIVRE_AVEC_RESERVES,
            VenteStatut.RESERVES_LEVEES,
            VenteStatut.LIVRE_DEFINITIF);

    /** Excluded from the pre-ACTE pipeline sum: confirmed stages + terminal loss. */
    private static final List<VenteStatut> EXCLUDED_FROM_PIPELINE = List.of(
            VenteStatut.ACTE,
            VenteStatut.LIVRE_AVEC_RESERVES,
            VenteStatut.RESERVES_LEVEES,
            VenteStatut.LIVRE_DEFINITIF,
            VenteStatut.ANNULE);

    /** Terminal states — everything else counts as an active vente. */
    private static final List<VenteStatut> TERMINAL_STATUTS = List.of(
            VenteStatut.LIVRE_DEFINITIF,
            VenteStatut.ANNULE);

    /** Same stalled definition as the home dashboard: COMPROMIS/FINANCEMENT idle 30+ days. */
    private static final List<VenteStatut> STALLED_STATUTS = List.of(
            VenteStatut.COMPROMIS,
            VenteStatut.FINANCEMENT);
    private static final int STALLED_DAYS = 30;

    private final PropertyRepository propertyRepository;
    private final VenteRepository venteRepository;
    private final VenteEcheanceRepository echeanceRepository;

    public GroupSocieteSummarizer(PropertyRepository propertyRepository,
                                  VenteRepository venteRepository,
                                  VenteEcheanceRepository echeanceRepository) {
        this.propertyRepository = propertyRepository;
        this.venteRepository = venteRepository;
        this.echeanceRepository = echeanceRepository;
    }

    @Transactional(readOnly = true)
    public SocieteRow summarize(Societe societe) {
        UUID societeId = societe.getId();
        LocalDate today = LocalDate.now();

        // Stock
        Map<PropertyStatus, Long> units = new EnumMap<>(PropertyStatus.class);
        for (Object[] row : propertyRepository.countByStatus(societeId)) {
            units.put((PropertyStatus) row[0], (Long) row[1]);
        }
        long disponibles = units.getOrDefault(PropertyStatus.ACTIVE, 0L);
        long reserves    = units.getOrDefault(PropertyStatus.RESERVED, 0L);
        long vendus      = units.getOrDefault(PropertyStatus.SOLD, 0L);
        double absorption = absorptionPct(disponibles, reserves, vendus);

        // Revenue
        BigDecimal caConfirme = CONFIRMED_STATUTS.stream()
                .map(s -> nz(venteRepository.sumPrixVenteByStatut(societeId, s)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal caEnCours = nz(venteRepository.sumPrixVente(societeId, EXCLUDED_FROM_PIPELINE));

        // Pipeline health
        long ventesActives = venteRepository.countByStatut(societeId, TERMINAL_STATUTS).stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        long ventesStallees = venteRepository.countStalledVentes(
                societeId, STALLED_STATUTS, LocalDateTime.now().minusDays(STALLED_DAYS));

        // Cash
        BigDecimal encaisse   = nz(echeanceRepository.sumPaidAll(societeId));
        BigDecimal aEncaisser = nz(echeanceRepository.sumDueAll(societeId));
        BigDecimal enRetard   = nz(echeanceRepository.sumMontantOverdue(societeId, today));
        long enRetardCount    = echeanceRepository.countOverdue(societeId, today);

        // VEFA alerts
        long optionsActives       = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.OPTION);
        long retractationsEnCours = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.EN_RETRACTATION);

        return new SocieteRow(
                societeId, societe.getNom(),
                disponibles, reserves, vendus, absorption,
                caConfirme, caEnCours,
                ventesActives, ventesStallees,
                encaisse, aEncaisser, enRetard, enRetardCount,
                optionsActives, retractationsEnCours);
    }

    /** Canonical absorption formula — must stay aligned with HomeDashboardDTO / absorption.ts. */
    static double absorptionPct(long disponibles, long reserves, long vendus) {
        long denominator = disponibles + reserves + vendus;
        if (denominator == 0) return 0d;
        return Math.round(vendus * 1000d / denominator) / 10d;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
