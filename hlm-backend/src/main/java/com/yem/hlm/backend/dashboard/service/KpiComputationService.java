package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.common.event.EcheanceChangedEvent;
import com.yem.hlm.backend.common.event.SaleFinalizedEvent;
import com.yem.hlm.backend.dashboard.domain.KpiSnapshot;
import com.yem.hlm.backend.dashboard.repo.KpiSnapshotRepository;
import com.yem.hlm.backend.vente.domain.EcheanceStatut;
import com.yem.hlm.backend.vente.domain.VenteEcheance;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Listens for domain events and recomputes KPI snapshots after each commit.
 *
 * <p>Runs in a new transaction (REQUIRES_NEW) so the snapshot write is isolated
 * from the triggering transaction and survives if it already committed.
 */
@Service
public class KpiComputationService {

    private static final Logger log = LoggerFactory.getLogger(KpiComputationService.class);

    private final KpiSnapshotRepository snapshotRepository;
    private final VenteRepository venteRepository;
    private final VenteEcheanceRepository echeanceRepository;
    private final PropertyRepository propertyRepository;

    public KpiComputationService(
            KpiSnapshotRepository snapshotRepository,
            VenteRepository venteRepository,
            VenteEcheanceRepository echeanceRepository,
            PropertyRepository propertyRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.venteRepository    = venteRepository;
        this.echeanceRepository = echeanceRepository;
        this.propertyRepository = propertyRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSaleFinalized(SaleFinalizedEvent event) {
        if (event.getTrancheId() == null) return;
        log.debug("KPI recompute triggered by SaleFinalizedEvent tranche={}", event.getTrancheId());
        recompute(event.getSocieteId(), event.getTrancheId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEcheanceChanged(EcheanceChangedEvent event) {
        if (event.getTrancheId() == null) return;
        log.debug("KPI recompute triggered by EcheanceChangedEvent tranche={}", event.getTrancheId());
        recomputeReceipts(event.getSocieteId(), event.getTrancheId());
    }

    // ── Public query ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KpiSnapshot getLatest(UUID societeId, UUID trancheId) {
        return snapshotRepository.findBySocieteIdAndTrancheId(societeId, trancheId)
                .orElseGet(() -> {
                    // Compute on demand if no snapshot exists yet
                    recompute(societeId, trancheId);
                    return snapshotRepository.findBySocieteIdAndTrancheId(societeId, trancheId)
                            .orElse(emptySnapshot(societeId, trancheId));
                });
    }

    // ── Computation internals ─────────────────────────────────────────────────

    /**
     * Full recompute: commercialisation rate + payment KPIs + avg sale delay.
     */
    private void recompute(UUID societeId, UUID trancheId) {
        KpiSnapshot snap = snapshotRepository.findBySocieteIdAndTrancheId(societeId, trancheId)
                .orElse(new KpiSnapshot(societeId, trancheId));

        // ── Taux de commercialisation ───────────────────────────────────────
        long totalUnits = propertyRepository.countBySocieteIdAndTrancheIdAndDeletedAtIsNull(societeId, trancheId);
        long soldOrReserved = propertyRepository.countBySocieteIdAndTrancheIdAndStatusIn(
                societeId, trancheId,
                List.of(PropertyStatus.RESERVED, PropertyStatus.SOLD));
        BigDecimal tauxComm = totalUnits == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(soldOrReserved * 100.0 / totalUnits).setScale(2, RoundingMode.HALF_UP);
        snap.setTauxCommercialisation(tauxComm);

        // ── Payment KPIs ────────────────────────────────────────────────────
        computeReceipts(snap, societeId, trancheId);

        // ── Délai moyen (reservation → vente) ──────────────────────────────
        List<Object[]> rows = venteRepository.findReservationToVenteDaysBySocieteAndTrancheId(societeId, trancheId);
        if (!rows.isEmpty()) {
            double avg = rows.stream()
                    .mapToLong(r -> r[0] instanceof Number n ? n.longValue() : 0L)
                    .average()
                    .orElse(0);
            snap.setDelaiMoyenVenteJours((int) Math.round(avg));
        }

        snap.setComputedAt(LocalDateTime.now());
        snapshotRepository.save(snap);
    }

    /**
     * Partial recompute: payment KPIs only (montant encaissé, taux recouvrement, solde).
     */
    private void recomputeReceipts(UUID societeId, UUID trancheId) {
        KpiSnapshot snap = snapshotRepository.findBySocieteIdAndTrancheId(societeId, trancheId)
                .orElse(new KpiSnapshot(societeId, trancheId));
        computeReceipts(snap, societeId, trancheId);
        snap.setComputedAt(LocalDateTime.now());
        snapshotRepository.save(snap);
    }

    private void computeReceipts(KpiSnapshot snap, UUID societeId, UUID trancheId) {
        List<VenteEcheance> allEcheances = echeanceRepository
                .findAllBySocieteIdAndTrancheId(societeId, trancheId);

        BigDecimal totalDu = allEcheances.stream()
                .map(VenteEcheance::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal encaisse = allEcheances.stream()
                .filter(e -> e.getStatut() == EcheanceStatut.PAYEE)
                .map(VenteEcheance::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal solde = totalDu.subtract(encaisse);

        BigDecimal tauxRecouv = totalDu.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : encaisse.multiply(BigDecimal.valueOf(100))
                          .divide(totalDu, 2, RoundingMode.HALF_UP);

        snap.setMontantEncaisse(encaisse);
        snap.setSoldeRestant(solde);
        snap.setTauxRecouvrement(tauxRecouv);
    }

    private KpiSnapshot emptySnapshot(UUID societeId, UUID trancheId) {
        KpiSnapshot s = new KpiSnapshot(societeId, trancheId);
        s.setTauxCommercialisation(BigDecimal.ZERO);
        s.setMontantEncaisse(BigDecimal.ZERO);
        s.setTauxRecouvrement(BigDecimal.ZERO);
        s.setSoldeRestant(BigDecimal.ZERO);
        s.setDelaiMoyenVenteJours(0);
        return s;
    }
}
