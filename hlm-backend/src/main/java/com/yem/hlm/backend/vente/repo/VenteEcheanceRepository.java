package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.VenteEcheance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenteEcheanceRepository extends JpaRepository<VenteEcheance, UUID> {

    Optional<VenteEcheance> findBySocieteIdAndId(UUID societeId, UUID id);

    List<VenteEcheance> findAllByVente_IdOrderByDateEcheanceAsc(UUID venteId);

    /** Sum of unpaid échéances with due date in [from, to) — for upcoming 30-day cash forecast. */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
              AND e.dateEcheance >= :from
              AND e.dateEcheance < :to
            """)
    java.math.BigDecimal sumMontantDueInPeriod(@Param("societeId") UUID societeId,
                                               @Param("from") java.time.LocalDate from,
                                               @Param("to") java.time.LocalDate to);

    /** Sum of unpaid échéances with due date before today (overdue). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
              AND e.dateEcheance < :today
            """)
    java.math.BigDecimal sumMontantOverdue(@Param("societeId") UUID societeId,
                                           @Param("today") java.time.LocalDate today);

    /** Count unpaid échéances with due date before today (overdue). */
    @Query("""
            SELECT COUNT(e)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
              AND e.dateEcheance < :today
            """)
    long countOverdue(@Param("societeId") UUID societeId,
                      @Param("today") java.time.LocalDate today);

    /**
     * Sum of PAID échéances whose ACTUAL payment date falls in [from, to).
     * Uses datePaiement (not dateEcheance) so late payments are assigned to the
     * correct accounting period. Used for the "encaissé ce mois" dashboard KPI.
     */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId    = :societeId
              AND e.statut       = com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
              AND e.datePaiement IS NOT NULL
              AND e.datePaiement >= :from
              AND e.datePaiement <  :to
            """)
    java.math.BigDecimal sumPaidInPeriod(@Param("societeId") UUID societeId,
                                         @Param("from") java.time.LocalDate from,
                                         @Param("to") java.time.LocalDate to);

    /**
     * Sum of all échéance amounts (paid + unpaid) whose due date falls in [from, to).
     * Denominator for the collection-efficiency KPI on the owner executive view.
     */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.dateEcheance >= :from
              AND e.dateEcheance <  :to
            """)
    java.math.BigDecimal sumMontantDueInPeriodAll(@Param("societeId") UUID societeId,
                                                  @Param("from") java.time.LocalDate from,
                                                  @Param("to") java.time.LocalDate to);

    /**
     * Returns aging buckets for unpaid échéances — used by the créances dashboard.
     * Columns: 0=current(future), 1=1-30d, 2=31-60d, 3=61-90d, 4=>90d,
     *          5=totalOutstanding(all unpaid), 6=totalEncaisse(all paid, all time).
     */
    @Query(value = """
            SELECT
              COALESCE(SUM(CASE WHEN date_echeance >= CURRENT_DATE THEN montant ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN CURRENT_DATE - date_echeance BETWEEN 1 AND 30 THEN montant ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN CURRENT_DATE - date_echeance BETWEEN 31 AND 60 THEN montant ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN CURRENT_DATE - date_echeance BETWEEN 61 AND 90 THEN montant ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN CURRENT_DATE - date_echeance > 90 THEN montant ELSE 0 END), 0),
              COALESCE(SUM(montant), 0),
              0
            FROM vente_echeance
            WHERE societe_id = :societeId AND statut != 'PAYEE'
            """, nativeQuery = true)
    Object[] getVenteReceivablesAging(@Param("societeId") UUID societeId);

    /**
     * Returns all écheances for ventes whose property belongs to the given tranche.
     * Used by KpiComputationService to compute payment KPIs at tranche granularity.
     */
    @Query("""
            SELECT e FROM VenteEcheance e
            JOIN e.vente v
            JOIN Property p ON p.id = v.propertyId
            WHERE e.societeId = :societeId
              AND p.trancheId = :trancheId
            """)
    List<VenteEcheance> findAllBySocieteIdAndTrancheId(
            @Param("societeId") UUID societeId,
            @Param("trancheId") UUID trancheId);
}
