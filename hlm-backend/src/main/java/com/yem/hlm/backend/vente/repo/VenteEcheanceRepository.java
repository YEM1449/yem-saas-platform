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

    /** True if a legal échéancier (etape set) was already generated for this vente. */
    boolean existsByVente_IdAndEtapeIsNotNull(UUID venteId);

    /** Sum of all échéance amounts for a vente (cumul vs price legal check, Art. 618-17). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId AND e.vente.id = :venteId
            """)
    java.math.BigDecimal sumMontantByVente(@Param("societeId") UUID societeId,
                                           @Param("venteId") UUID venteId);

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

    /**
     * Sum of unpaid échéances grouped by due month, for the cash-forecast timeline.
     * Only échéances in [from, to) are counted, so passing {@code from = today} keeps
     * overdue amounts (already shown as "en retard") out of the forecast.
     * Rows: [year(Integer), month(Integer 1-12), montant(BigDecimal)]; months with no
     * unpaid échéance are simply absent (gaps are zero-filled in the service).
     */
    @Query(value = """
            SELECT CAST(EXTRACT(YEAR  FROM date_echeance) AS INTEGER) AS yr,
                   CAST(EXTRACT(MONTH FROM date_echeance) AS INTEGER) AS mo,
                   COALESCE(SUM(montant), 0)                          AS total
            FROM vente_echeance
            WHERE societe_id = :societeId
              AND statut != 'PAYEE'
              AND date_echeance >= :from
              AND date_echeance <  :to
            GROUP BY yr, mo
            ORDER BY yr, mo
            """, nativeQuery = true)
    List<Object[]> sumUnpaidByMonth(@Param("societeId") UUID societeId,
                                    @Param("from") java.time.LocalDate from,
                                    @Param("to") java.time.LocalDate to);

    /** All-time encaissé — sum of PAID échéances (trésorerie dashboard). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut = com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
            """)
    java.math.BigDecimal sumPaidAll(@Param("societeId") UUID societeId);

    /** All-time à encaisser — sum of UNPAID échéances (trésorerie dashboard). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
            """)
    java.math.BigDecimal sumDueAll(@Param("societeId") UUID societeId);

    /**
     * Overdue unpaid échéances with sale + buyer context, oldest first.
     * Rows: [venteId(UUID), venteRef(String), acquereur(String), libelle(String),
     *        montant(BigDecimal), dateEcheance(LocalDate)].
     */
    @Query("""
            SELECT e.vente.id, e.vente.venteRef, e.vente.contact.fullName,
                   e.libelle, e.montant, e.dateEcheance
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
              AND e.dateEcheance < :today
            ORDER BY e.dateEcheance ASC
            """)
    List<Object[]> findOverdueDetails(@Param("societeId") UUID societeId,
                                      @Param("today") java.time.LocalDate today,
                                      org.springframework.data.domain.Pageable pageable);

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
     * Single row: col 0=current(future), 1=1-30d, 2=31-60d, 3=61-90d, 4=>90d,
     *             5=totalOutstanding(all unpaid), 6=hardcoded 0 (placeholder).
     * Return type is List<Object[]> (single element) — avoids Spring Data JPA
     * ambiguity with Object[] return type on single-row native queries.
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
    List<Object[]> getVenteReceivablesAging(@Param("societeId") UUID societeId);

    // =========================================================================
    // Créances dashboard — VenteEcheance-based queries
    // =========================================================================

    /**
     * Outstanding + overdue totals for the main créances KPIs.
     * Single row: [totalOutstanding(BigDecimal), totalOverdue(BigDecimal)].
     */
    @Query(value = """
            SELECT
              COALESCE(SUM(montant), 0),
              COALESCE(SUM(CASE WHEN date_echeance < CURRENT_DATE THEN montant ELSE 0 END), 0)
            FROM vente_echeance
            WHERE societe_id = :societeId AND statut != 'PAYEE'
            """, nativeQuery = true)
    List<Object[]> venteReceivablesTotals(@Param("societeId") UUID societeId);

    /**
     * Raw [montant, dateEcheance] pairs for unpaid échéances —
     * consumed by buildAgingBuckets() in ReceivablesDashboardService.
     */
    @Query("""
            SELECT e.montant, e.dateEcheance
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut <> com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
            """)
    List<Object[]> venteOutstandingForAging(@Param("societeId") UUID societeId);

    /**
     * Overdue amounts grouped by project — top 10 for the bar chart.
     * Rows: [projectId(UUID), projectName(String), overdueAmount(BigDecimal)].
     */
    @Query(value = """
            SELECT proj.id, proj.name, COALESCE(SUM(e.montant), 0)
            FROM vente_echeance e
            JOIN vente v    ON v.id    = e.vente_id
            JOIN property pr ON pr.id  = v.property_id
            JOIN project proj ON proj.id = pr.project_id
            WHERE e.societe_id = :societeId
              AND e.statut != 'PAYEE'
              AND e.date_echeance < CURRENT_DATE
            GROUP BY proj.id, proj.name
            ORDER BY SUM(e.montant) DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> venteOverdueByProject(@Param("societeId") UUID societeId);

    /** Sum of all échéance amounts (denominator for collection rate). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
            """)
    java.math.BigDecimal venteTotalIssued(@Param("societeId") UUID societeId);

    /** Sum of all paid échéance amounts (numerator for collection rate). */
    @Query("""
            SELECT COALESCE(SUM(e.montant), 0)
            FROM VenteEcheance e
            WHERE e.societeId = :societeId
              AND e.statut = com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE
            """)
    java.math.BigDecimal venteTotalReceived(@Param("societeId") UUID societeId);

    /**
     * Last 10 paid échéances for the recent-payments table.
     * Rows: [id, montant, datePaiement, method('Virement'), projectName, propertyRef, agentEmail].
     */
    @Query(value = """
            SELECT e.id, e.montant, e.date_paiement,
                   'Virement'                         AS method,
                   COALESCE(proj.name,    '—')        AS project_name,
                   COALESCE(pr.reference_code, '—')   AS property_ref,
                   u.email                            AS agent_email
            FROM vente_echeance e
            JOIN vente     v    ON v.id    = e.vente_id
            JOIN app_user  u    ON u.id    = v.agent_id
            LEFT JOIN property  pr   ON pr.id   = v.property_id
            LEFT JOIN project   proj ON proj.id  = pr.project_id
            WHERE e.societe_id = :societeId
              AND e.statut     = 'PAYEE'
              AND e.date_paiement IS NOT NULL
            ORDER BY e.date_paiement DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> venteRecentPayments(@Param("societeId") UUID societeId);

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
