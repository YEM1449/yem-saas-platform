package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenteRepository extends JpaRepository<Vente, UUID> {

    Optional<Vente> findBySocieteIdAndId(UUID societeId, UUID id);

    Optional<Vente> findBySocieteIdAndReservationId(UUID societeId, UUID reservationId);

    List<Vente> findAllBySocieteIdOrderByCreatedAtDesc(UUID societeId);

    List<Vente> findAllBySocieteIdAndStatutOrderByCreatedAtDesc(UUID societeId, VenteStatut statut);

    List<Vente> findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(UUID societeId, UUID contactId);

    boolean existsBySocieteIdAndPropertyIdAndStatutNot(UUID societeId, UUID propertyId, VenteStatut statut);

    long countBySocieteIdAndStatut(UUID societeId, VenteStatut statut);

    /** Active pipeline: all non-terminal ventes for agent scope (home dashboard). */
    List<Vente> findAllBySocieteIdAndAgent_IdAndStatutNotInOrderByCreatedAtDesc(
            UUID societeId, UUID agentId, List<VenteStatut> excluded);

    /** Pipeline breakdown: [statut, count] for all non-terminal ventes. */
    @Query("""
            SELECT v.statut, COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut NOT IN :excluded
            GROUP BY v.statut
            """)
    List<Object[]> countByStatut(@Param("societeId") UUID societeId,
                                 @Param("excluded") List<VenteStatut> excluded);

    /** Pipeline breakdown scoped to one agent. */
    @Query("""
            SELECT v.statut, COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.agent.id  = :agentId
              AND v.statut NOT IN :excluded
            GROUP BY v.statut
            """)
    List<Object[]> countByStatutForAgent(@Param("societeId") UUID societeId,
                                         @Param("agentId") UUID agentId,
                                         @Param("excluded") List<VenteStatut> excluded);

    /** Total CA (prixVente sum) for active pipeline. */
    @Query("""
            SELECT COALESCE(SUM(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut NOT IN :excluded
            """)
    java.math.BigDecimal sumPrixVente(@Param("societeId") UUID societeId,
                                      @Param("excluded") List<VenteStatut> excluded);

    /** Total CA for agent's active pipeline. */
    @Query("""
            SELECT COALESCE(SUM(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.agent.id  = :agentId
              AND v.statut NOT IN :excluded
            """)
    java.math.BigDecimal sumPrixVenteForAgent(@Param("societeId") UUID societeId,
                                               @Param("agentId") UUID agentId,
                                               @Param("excluded") List<VenteStatut> excluded);

    /** CA signed for ventes created in a time window (for monthly trend). */
    @Query("""
            SELECT COALESCE(SUM(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.createdAt >= :from
              AND v.createdAt < :to
              AND v.statut NOT IN :excluded
            """)
    java.math.BigDecimal sumPrixVenteInPeriod(@Param("societeId") UUID societeId,
                                              @Param("from") java.time.LocalDateTime from,
                                              @Param("to") java.time.LocalDateTime to,
                                              @Param("excluded") List<VenteStatut> excluded);

    /** Same, scoped to one agent. */
    @Query("""
            SELECT COALESCE(SUM(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.agent.id  = :agentId
              AND v.createdAt >= :from
              AND v.createdAt < :to
              AND v.statut NOT IN :excluded
            """)
    java.math.BigDecimal sumPrixVenteInPeriodForAgent(@Param("societeId") UUID societeId,
                                                      @Param("agentId") UUID agentId,
                                                      @Param("from") java.time.LocalDateTime from,
                                                      @Param("to") java.time.LocalDateTime to,
                                                      @Param("excluded") List<VenteStatut> excluded);

    /** CA for ventes with a specific statut (e.g. LIVRE for total realized CA). */
    @Query("""
            SELECT COALESCE(SUM(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut = :statut
            """)
    java.math.BigDecimal sumPrixVenteByStatut(@Param("societeId") UUID societeId,
                                              @Param("statut") VenteStatut statut);

    /** Count ventes stuck in early pipeline stages — no movement since before :before. */
    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut IN :statuts
              AND v.stageEntryDate < :before
            """)
    long countStalledVentes(@Param("societeId") UUID societeId,
                             @Param("statuts") List<VenteStatut> statuts,
                             @Param("before") java.time.LocalDateTime before);

    /** Recent ventes for agent (home widget). */
    @Query("""
            SELECT v FROM Vente v
            WHERE v.societeId = :societeId
              AND v.agent.id  = :agentId
            ORDER BY v.createdAt DESC
            """)
    List<Vente> findRecentForAgent(@Param("societeId") UUID societeId,
                                   @Param("agentId") UUID agentId,
                                   org.springframework.data.domain.Pageable pageable);

    /**
     * Returns the number of days between reservation date and vente creation for each
     * vente linked to a reservation, within the given tranche. Used for délai moyen KPI.
     * Result rows: [daysBetween (Long)].
     */
    @Query(value = """
            SELECT EXTRACT(DAY FROM (v.created_at - r.reservation_date::timestamp))
            FROM vente v
            JOIN property p ON p.id = v.property_id
            JOIN property_reservation r ON r.id = v.reservation_id
            WHERE v.societe_id  = :societeId
              AND p.tranche_id  = :trancheId
              AND v.reservation_id IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> findReservationToVenteDaysBySocieteAndTrancheId(
            @Param("societeId") UUID societeId,
            @Param("trancheId") UUID trancheId);

    /** Count ventes created in [from, to) optionally filtered by statut (for cancellation rate). */
    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.createdAt >= :from
              AND v.createdAt < :to
            """)
    long countCreatedInPeriod(@Param("societeId") UUID societeId,
                              @Param("from") java.time.LocalDateTime from,
                              @Param("to") java.time.LocalDateTime to);

    /** Count ventes created in [from, to) excluding given statuts (e.g. ANNULE). */
    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.createdAt >= :from
              AND v.createdAt < :to
              AND v.statut NOT IN :excluded
            """)
    long countInPeriodExcluding(@Param("societeId") UUID societeId,
                                @Param("from") java.time.LocalDateTime from,
                                @Param("to") java.time.LocalDateTime to,
                                @Param("excluded") List<VenteStatut> excluded);

    /** Same, scoped to one agent — for role-aware pacing metrics. */
    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.agent.id  = :agentId
              AND v.createdAt >= :from
              AND v.createdAt < :to
              AND v.statut NOT IN :excluded
            """)
    long countInPeriodExcludingForAgent(@Param("societeId") UUID societeId,
                                        @Param("agentId") UUID agentId,
                                        @Param("from") java.time.LocalDateTime from,
                                        @Param("to") java.time.LocalDateTime to,
                                        @Param("excluded") List<VenteStatut> excluded);

    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut    = :statut
              AND v.createdAt >= :from
              AND v.createdAt < :to
            """)
    long countByStatutInPeriod(@Param("societeId") UUID societeId,
                               @Param("statut") VenteStatut statut,
                               @Param("from") java.time.LocalDateTime from,
                               @Param("to") java.time.LocalDateTime to);

    /** Average prixVente for a given statut (e.g. average ticket size for LIVRE). */
    @Query("""
            SELECT COALESCE(AVG(v.prixVente), 0)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut    = :statut
            """)
    java.math.BigDecimal avgPrixVenteByStatut(@Param("societeId") UUID societeId,
                                              @Param("statut") VenteStatut statut);

    /**
     * Top agents by signed CA in [from, to). Excludes ANNULE.
     * Result rows: [agentId UUID, prenom String, nomFamille String, totalCA BigDecimal, ventesCount Long].
     */
    @Query("""
            SELECT v.agent.id, v.agent.prenom, v.agent.nomFamille,
                   COALESCE(SUM(v.prixVente), 0), COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.createdAt >= :from
              AND v.createdAt < :to
              AND v.statut <> com.yem.hlm.backend.vente.domain.VenteStatut.ANNULE
            GROUP BY v.agent.id, v.agent.prenom, v.agent.nomFamille
            ORDER BY SUM(v.prixVente) DESC
            """)
    List<Object[]> topAgentsByCA(@Param("societeId") UUID societeId,
                                 @Param("from") java.time.LocalDateTime from,
                                 @Param("to") java.time.LocalDateTime to,
                                 org.springframework.data.domain.Pageable pageable);

    /**
     * Weekly sparkline of CA signé (sum of prixVente per ISO week, ANNULE excluded).
     * Result rows: [weekStart java.sql.Date, total BigDecimal] ordered chronologically.
     */
    @Query(value = """
            SELECT date_trunc('week', v.created_at)::date AS week,
                   COALESCE(SUM(v.prix_vente), 0) AS total
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.created_at >= :from
              AND v.statut <> 'ANNULE'
            GROUP BY week
            ORDER BY week
            """, nativeQuery = true)
    List<Object[]> sumPrixVenteByWeek(@Param("societeId") UUID societeId,
                                      @Param("from") java.time.LocalDateTime from);

    /**
     * Weekly sparkline of vente count (created per ISO week, all statuses).
     * Result rows: [weekStart java.sql.Date, count Long] ordered chronologically.
     */
    @Query(value = """
            SELECT date_trunc('week', v.created_at)::date AS week,
                   COUNT(*) AS total
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.created_at >= :from
            GROUP BY week
            ORDER BY week
            """, nativeQuery = true)
    List<Object[]> countCreatedByWeek(@Param("societeId") UUID societeId,
                                      @Param("from") java.time.LocalDateTime from);

    /**
     * Pipeline analysis — per-statut aggregates for non-terminal ventes.
     * Rows: [statut(String), count(Long), rawCA(BigDecimal), weightedCA(BigDecimal),
     *        probability(Integer), avgAgingDays(Double)].
     */
    @Query(value = """
            SELECT v.statut,
                   COUNT(*),
                   COALESCE(SUM(v.prix_vente), 0),
                   COALESCE(SUM(v.prix_vente * v.probability / 100.0), 0),
                   v.probability,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (NOW() - v.stage_entry_date)) / 86400.0), 0)
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.statut NOT IN ('LIVRE','ANNULE')
            GROUP BY v.statut, v.probability
            ORDER BY v.probability
            """, nativeQuery = true)
    List<Object[]> pipelineAnalysis(@Param("societeId") UUID societeId);

    /**
     * At-risk deals: non-terminal ventes that have been in their current stage
     * for more than :thresholdDays days. Max 20 results ordered by aging desc.
     */
    @Query(value = """
            SELECT v.id, v.vente_ref, c.first_name || ' ' || c.last_name,
                   v.statut, v.prix_vente, v.probability,
                   EXTRACT(EPOCH FROM (NOW() - v.stage_entry_date)) / 86400.0 AS aging
            FROM vente v
            JOIN contact c ON c.id = v.contact_id
            WHERE v.societe_id = :societeId
              AND v.statut NOT IN ('LIVRE','ANNULE')
              AND EXTRACT(EPOCH FROM (NOW() - v.stage_entry_date)) / 86400.0 > :thresholdDays
            ORDER BY aging DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Object[]> atRiskDeals(@Param("societeId") UUID societeId,
                               @Param("thresholdDays") double thresholdDays);

    /**
     * Forecast: weighted CA grouped by closing horizon bucket.
     * Rows: [expectedClosingDate(Date|null), prixVente(BigDecimal), probability(Integer)].
     * Returns only active (non-terminal) deals.
     */
    @Query(value = """
            SELECT v.expected_closing_date, v.prix_vente, v.probability
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.statut NOT IN ('LIVRE','ANNULE')
            """, nativeQuery = true)
    List<Object[]> forecastRawData(@Param("societeId") UUID societeId);

    /**
     * Agent performance: per-agent stats across all ventes.
     * Rows: [agentId(UUID), prenom(String), nomFamille(String),
     *        livreCount(Long), totalCA(BigDecimal), annuleCount(Long),
     *        avgDaysToClose(Double), activeCount(Long)].
     */
    @Query(value = """
            SELECT v.agent_id,
                   u.prenom, u.nom_famille,
                   COUNT(*) FILTER (WHERE v.statut = 'LIVRE'),
                   COALESCE(SUM(v.prix_vente) FILTER (WHERE v.statut = 'LIVRE'), 0),
                   COUNT(*) FILTER (WHERE v.statut = 'ANNULE'),
                   AVG(EXTRACT(EPOCH FROM (COALESCE(v.date_livraison_reelle::timestamp, v.stage_entry_date) - v.created_at)) / 86400.0) FILTER (WHERE v.statut = 'LIVRE'),
                   COUNT(*) FILTER (WHERE v.statut NOT IN ('LIVRE','ANNULE'))
            FROM vente v
            JOIN app_user u ON u.id = v.agent_id
            WHERE v.societe_id = :societeId
            GROUP BY v.agent_id, u.prenom, u.nom_famille
            ORDER BY SUM(v.prix_vente) FILTER (WHERE v.statut = 'LIVRE') DESC NULLS LAST
            """, nativeQuery = true)
    List<Object[]> agentPerformance(@Param("societeId") UUID societeId);

    /**
     * Discount summary: compares property.price (list) vs vente.prix_vente (agreed).
     * Rows: [dealsWithDiscount(Long), totalDeals(Long), avgDiscountPct(Double),
     *        maxDiscountPct(Double), totalDiscountVolume(BigDecimal)].
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (WHERE p.price > v.prix_vente),
                   COUNT(*),
                   AVG(CASE WHEN p.price > v.prix_vente AND p.price > 0
                       THEN (p.price - v.prix_vente) / p.price * 100 END),
                   MAX(CASE WHEN p.price > v.prix_vente AND p.price > 0
                       THEN (p.price - v.prix_vente) / p.price * 100 ELSE 0 END),
                   COALESCE(SUM(CASE WHEN p.price > v.prix_vente
                       THEN p.price - v.prix_vente ELSE 0 END), 0)
            FROM vente v
            JOIN property p ON p.id = v.property_id AND p.societe_id = v.societe_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND p.price IS NOT NULL AND v.prix_vente IS NOT NULL AND p.price > 0
            """, nativeQuery = true)
    List<Object[]> discountSummary(@Param("societeId") UUID societeId);

    /**
     * Discount breakdown per agent.
     * Rows: [agentId(UUID), prenom(String), nomFamille(String),
     *        dealsWithDiscount(Long), totalDeals(Long), avgDiscountPct(Double),
     *        totalDiscountVolume(BigDecimal)].
     */
    @Query(value = """
            SELECT v.agent_id, u.prenom, u.nom_famille,
                   COUNT(*) FILTER (WHERE p.price > v.prix_vente),
                   COUNT(*),
                   AVG(CASE WHEN p.price > v.prix_vente AND p.price > 0
                       THEN (p.price - v.prix_vente) / p.price * 100 END),
                   COALESCE(SUM(CASE WHEN p.price > v.prix_vente
                       THEN p.price - v.prix_vente ELSE 0 END), 0)
            FROM vente v
            JOIN property p ON p.id = v.property_id AND p.societe_id = v.societe_id
            JOIN app_user u ON u.id = v.agent_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND p.price IS NOT NULL AND v.prix_vente IS NOT NULL AND p.price > 0
            GROUP BY v.agent_id, u.prenom, u.nom_famille
            ORDER BY SUM(CASE WHEN p.price > v.prix_vente
                        THEN p.price - v.prix_vente ELSE 0 END) DESC
            """, nativeQuery = true)
    List<Object[]> discountByAgent(@Param("societeId") UUID societeId);

    /**
     * Counts ventes with SRU or condition-suspensive-crédit deadlines expiring within
     * {@code daysAhead} calendar days. Only active (non-terminal) ventes are considered.
     *
     * <p>Returns rows: [alert_type STRING, cnt LONG]
     * where alert_type is "SRU" or "CREDIT".
     */
    @Query(value = """
            SELECT 'SRU' AS alert_type, COUNT(*) AS cnt
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.statut NOT IN ('LIVRE', 'ANNULE')
              AND v.date_fin_delai_sru IS NOT NULL
              AND v.date_fin_delai_sru BETWEEN CURRENT_DATE AND CURRENT_DATE + CAST(:daysAhead AS INT)
            UNION ALL
            SELECT 'CREDIT' AS alert_type, COUNT(*) AS cnt
            FROM vente v
            WHERE v.societe_id = :societeId
              AND v.statut NOT IN ('LIVRE', 'ANNULE')
              AND v.date_limite_condition_credit IS NOT NULL
              AND v.date_limite_condition_credit BETWEEN CURRENT_DATE AND CURRENT_DATE + CAST(:daysAhead AS INT)
            """, nativeQuery = true)
    List<Object[]> countExpiringDeadlines(@Param("societeId") UUID societeId,
                                          @Param("daysAhead") int daysAhead);

    // =========================================================================
    // Commercial dashboard queries
    // =========================================================================

    /**
     * Sales totals for the commercial dashboard (non-ANNULE ventes in date range).
     * Rows: [count(Long), totalAmount(BigDecimal), avgAmount(BigDecimal)].
     * CAST(:x AS uuid) IS NULL pattern handles nullable UUID params in native SQL.
     */
    @Query(value = """
            SELECT COUNT(v.id), COALESCE(SUM(v.prix_vente), 0), COALESCE(AVG(v.prix_vente), 0)
            FROM vente v
            LEFT JOIN property pr ON pr.id = v.property_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:projectId AS uuid) IS NULL OR pr.project_id = CAST(:projectId AS uuid))
              AND (CAST(:agentId  AS uuid) IS NULL OR v.agent_id   = CAST(:agentId  AS uuid))
            """, nativeQuery = true)
    List<Object[]> venteSalesTotals(@Param("societeId") UUID societeId,
                                    @Param("from") java.time.LocalDateTime from,
                                    @Param("to") java.time.LocalDateTime to,
                                    @Param("projectId") UUID projectId,
                                    @Param("agentId") UUID agentId);

    /**
     * Paginated drill-down table of ventes for the commercial dashboard.
     * Rows: [id(UUID), createdAt(Timestamp), projectName(String), propertyRef(String),
     *        buyerName(String), agentEmail(String), prixVente(BigDecimal)].
     */
    @Query(value = """
            SELECT v.id, v.created_at, proj.name, pr.reference_code,
                   c.first_name || ' ' || c.last_name, u.email, v.prix_vente
            FROM vente v
            LEFT JOIN property pr   ON pr.id   = v.property_id
            LEFT JOIN project  proj ON proj.id  = pr.project_id
            LEFT JOIN contact  c    ON c.id     = v.contact_id
            LEFT JOIN app_user u    ON u.id     = v.agent_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:projectId AS uuid) IS NULL OR proj.id    = CAST(:projectId AS uuid))
              AND (CAST(:agentId  AS uuid) IS NULL OR v.agent_id  = CAST(:agentId  AS uuid))
            ORDER BY v.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(v.id)
            FROM vente v
            LEFT JOIN property pr   ON pr.id   = v.property_id
            LEFT JOIN project  proj ON proj.id  = pr.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:projectId AS uuid) IS NULL OR proj.id    = CAST(:projectId AS uuid))
              AND (CAST(:agentId  AS uuid) IS NULL OR v.agent_id  = CAST(:agentId  AS uuid))
            """,
            nativeQuery = true)
    org.springframework.data.domain.Page<Object[]> venteSalesForTable(
            @Param("societeId") UUID societeId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId") UUID agentId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Top-10 project breakdown for the commercial dashboard.
     * Rows: [projectId(UUID), projectName(String), count(Long), totalCA(BigDecimal)].
     */
    @Query(value = """
            SELECT proj.id, proj.name, COUNT(v.id), COALESCE(SUM(v.prix_vente), 0)
            FROM vente v
            LEFT JOIN property pr   ON pr.id  = v.property_id
            LEFT JOIN project  proj ON proj.id = pr.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:agentId AS uuid) IS NULL OR v.agent_id = CAST(:agentId AS uuid))
            GROUP BY proj.id, proj.name
            ORDER BY SUM(v.prix_vente) DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> venteSalesByProject(@Param("societeId") UUID societeId,
                                       @Param("from") java.time.LocalDateTime from,
                                       @Param("to") java.time.LocalDateTime to,
                                       @Param("agentId") UUID agentId);

    /**
     * Top-10 agent breakdown for the commercial dashboard.
     * Rows: [agentId(UUID), agentName(String), count(Long), totalCA(BigDecimal)].
     */
    @Query(value = """
            SELECT v.agent_id, u.prenom || ' ' || u.nom_famille, COUNT(v.id),
                   COALESCE(SUM(v.prix_vente), 0)
            FROM vente v
            LEFT JOIN app_user u ON u.id     = v.agent_id
            LEFT JOIN property pr ON pr.id   = v.property_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:projectId AS uuid) IS NULL OR pr.project_id = CAST(:projectId AS uuid))
            GROUP BY v.agent_id, u.prenom, u.nom_famille
            ORDER BY SUM(v.prix_vente) DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> venteSalesByAgent(@Param("societeId") UUID societeId,
                                     @Param("from") java.time.LocalDateTime from,
                                     @Param("to") java.time.LocalDateTime to,
                                     @Param("projectId") UUID projectId);

    /**
     * Daily CA trend (non-ANNULE) for the sparkline chart.
     * Rows: [day(Date), totalCA(BigDecimal)].
     */
    @Query(value = """
            SELECT v.created_at::date, COALESCE(SUM(v.prix_vente), 0)
            FROM vente v
            LEFT JOIN property pr ON pr.id = v.property_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND v.created_at >= :from
              AND v.created_at < :to
              AND (CAST(:projectId AS uuid) IS NULL OR pr.project_id = CAST(:projectId AS uuid))
              AND (CAST(:agentId  AS uuid) IS NULL OR v.agent_id    = CAST(:agentId  AS uuid))
            GROUP BY v.created_at::date
            ORDER BY v.created_at::date
            """, nativeQuery = true)
    List<Object[]> venteSalesAmountByDay(@Param("societeId") UUID societeId,
                                         @Param("from") java.time.LocalDateTime from,
                                         @Param("to") java.time.LocalDateTime to,
                                         @Param("projectId") UUID projectId,
                                         @Param("agentId") UUID agentId);

    /**
     * Monthly CA signed trend — last N months (non-ANNULE).
     * Rows: [year(int), month(int), totalCA(BigDecimal)].
     */
    @Query(value = """
            SELECT EXTRACT(YEAR  FROM created_at)::int AS yr,
                   EXTRACT(MONTH FROM created_at)::int AS mo,
                   COALESCE(SUM(prix_vente), 0)         AS ca
            FROM vente
            WHERE societe_id = :societeId
              AND statut <> 'ANNULE'
              AND created_at >= :from
            GROUP BY yr, mo
            ORDER BY yr ASC, mo ASC
            """, nativeQuery = true)
    List<Object[]> monthlyCaTrend(@Param("societeId") UUID societeId,
                                   @Param("from") java.time.LocalDateTime from);

    /**
     * Top projects by CA signed (non-ANNULE, all time).
     * Rows: [projectId(UUID), projectName(String), totalCA(BigDecimal), ventesCount(long)].
     * Requires join through property → project.
     */
    @Query(value = """
            SELECT pr.project_id::text,
                   p.name                     AS project_name,
                   COALESCE(SUM(v.prix_vente), 0) AS total_ca,
                   COUNT(v.id)                AS ventes_count
            FROM vente v
            JOIN property pr ON pr.id = v.property_id
            JOIN project   p  ON p.id = pr.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
            GROUP BY pr.project_id, p.name
            ORDER BY total_ca DESC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> topProjectsByCA(@Param("societeId") UUID societeId);

    /**
     * Revenue and unit breakdown by property type (non-ANNULE ventes with joined property).
     * Rows: [propertyType(String), ventesCount(Long), totalCA(BigDecimal),
     *        avgPrix(BigDecimal), avgSurface(Double), avgPricePerSqm(Double)].
     */
    @Query(value = """
            SELECT p.type::text,
                   COUNT(v.id)                                                 AS ventes_count,
                   COALESCE(SUM(v.prix_vente), 0)                              AS total_ca,
                   COALESCE(AVG(v.prix_vente), 0)                              AS avg_prix,
                   AVG(p.surface_area_sqm)                                     AS avg_surface,
                   COALESCE(
                       AVG(CASE WHEN p.surface_area_sqm > 0
                           THEN v.prix_vente / p.surface_area_sqm END), 0)    AS avg_price_per_sqm
            FROM vente v
            JOIN property p ON p.id = v.property_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND p.price > 0
            GROUP BY p.type
            ORDER BY total_ca DESC
            """, nativeQuery = true)
    List<Object[]> salesBreakdownByType(@Param("societeId") UUID societeId);

    /**
     * Distribution of time-to-close (days from created_at to the immutable close marker).
     * Close marker priority: date_livraison_reelle (user-supplied date) → stage_entry_date
     * (timestamp stamped by VenteService.updateStatut() when LIVRE was recorded; LIVRE is
     * terminal so it never changes after delivery). updated_at is intentionally excluded
     * because post-delivery edits (notes, financing fields) would silently inflate durations.
     * Rows: [bucket(String), count(Long), avgDays(Double)].
     * Buckets: LT_30, D30_60, D61_90, D91_180, GT_180.
     */
    @Query(value = """
            SELECT bucket,
                   COUNT(*) AS cnt,
                   AVG(days_to_close) AS avg_days
            FROM (
                SELECT EXTRACT(EPOCH FROM (
                           COALESCE(date_livraison_reelle::timestamp, stage_entry_date)
                           - created_at
                       )) / 86400.0 AS days_to_close,
                       CASE
                           WHEN EXTRACT(EPOCH FROM (COALESCE(date_livraison_reelle::timestamp, stage_entry_date) - created_at)) / 86400.0 < 30  THEN 'LT_30'
                           WHEN EXTRACT(EPOCH FROM (COALESCE(date_livraison_reelle::timestamp, stage_entry_date) - created_at)) / 86400.0 < 60  THEN 'D30_60'
                           WHEN EXTRACT(EPOCH FROM (COALESCE(date_livraison_reelle::timestamp, stage_entry_date) - created_at)) / 86400.0 < 90  THEN 'D61_90'
                           WHEN EXTRACT(EPOCH FROM (COALESCE(date_livraison_reelle::timestamp, stage_entry_date) - created_at)) / 86400.0 < 180 THEN 'D91_180'
                           ELSE 'GT_180'
                       END AS bucket
                FROM vente
                WHERE societe_id = :societeId
                  AND statut = 'LIVRE'
            ) sub
            GROUP BY bucket
            ORDER BY MIN(days_to_close)
            """, nativeQuery = true)
    List<Object[]> timeToCloseBuckets(@Param("societeId") UUID societeId);

    /**
     * Average price per sqm by project (for delivered and active ventes).
     * Rows: [projectId(UUID), projectName(String), avgPricePerSqm(Double), sampleSize(Long)].
     */
    @Query(value = """
            SELECT prop.project_id::text,
                   p.name AS project_name,
                   COALESCE(AVG(CASE WHEN prop.surface_area_sqm > 0
                               THEN v.prix_vente / prop.surface_area_sqm END), 0) AS avg_price_sqm,
                   COUNT(v.id) AS sample_size
            FROM vente v
            JOIN property prop ON prop.id = v.property_id
            JOIN project p ON p.id = prop.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND prop.surface_area_sqm > 0
            GROUP BY prop.project_id, p.name
            HAVING COUNT(v.id) >= 1
            ORDER BY avg_price_sqm DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> avgPricePerSqmByProject(@Param("societeId") UUID societeId);

    /**
     * Pipeline aging per active statut — how long deals have been open since creation.
     * Excludes terminal statuts (LIVRE, ANNULE).
     * Rows: [statut(String), count(Long), avgDays(Double), maxDays(Double), stalled30dCount(Long), totalValue(BigDecimal)].
     * avgDays/maxDays = days since vente.created_at. stalled30dCount = deals older than 30 days.
     */
    @Query(value = """
            SELECT statut,
                   COUNT(*)                                                                        AS cnt,
                   AVG(EXTRACT(EPOCH FROM (NOW() - created_at)) / 86400.0)                        AS avg_days,
                   MAX(EXTRACT(EPOCH FROM (NOW() - created_at)) / 86400.0)                        AS max_days,
                   COUNT(CASE WHEN EXTRACT(EPOCH FROM (NOW() - created_at)) / 86400.0 > 30
                               THEN 1 END)                                                        AS stalled_30d,
                   COALESCE(SUM(prix_vente), 0)                                                   AS total_value
            FROM vente
            WHERE societe_id = :societeId
              AND statut NOT IN ('LIVRE','ANNULE')
            GROUP BY statut
            ORDER BY CASE statut
                       WHEN 'COMPROMIS'    THEN 1
                       WHEN 'FINANCEMENT'  THEN 2
                       WHEN 'ACTE_NOTARIE' THEN 3
                       ELSE 4
                     END
            """, nativeQuery = true)
    List<Object[]> pipelineAgingByStatut(@Param("societeId") UUID societeId);

    /**
     * Average days to close and average ticket per property type (LIVRE ventes only).
     * Uses COALESCE(date_livraison_reelle::timestamp, stage_entry_date) as close marker,
     * consistent with timeToCloseBuckets. Only includes ventes with a valid close timestamp.
     * Rows: [type(String), soldCount(Long), avgPrix(Double), avgDaysToClose(Double)].
     */
    @Query(value = """
            SELECT p.type::text,
                   COUNT(v.id)                                                          AS sold_count,
                   AVG(v.prix_vente)                                                    AS avg_prix,
                   AVG(EXTRACT(EPOCH FROM (
                           COALESCE(v.date_livraison_reelle::timestamp, v.stage_entry_date)
                           - v.created_at
                       )) / 86400.0)                                                    AS avg_days_to_close
            FROM vente v
            JOIN property p ON p.id = v.property_id
            WHERE v.societe_id = :societeId
              AND v.statut = 'LIVRE'
              AND COALESCE(v.date_livraison_reelle::timestamp, v.stage_entry_date) IS NOT NULL
            GROUP BY p.type
            ORDER BY avg_days_to_close ASC NULLS LAST
            """, nativeQuery = true)
    List<Object[]> avgDaysToCloseByPropertyType(@Param("societeId") UUID societeId);

    /**
     * CA and vente count grouped by tranche (via property.tranche_id).
     * Rows: [trancheId(text), trancheLabel(String), projectId(text), projectName(String),
     *        totalCA(BigDecimal), ventesCount(Long)].
     * Only includes ventes whose property is linked to a tranche.
     */
    @Query(value = """
            SELECT prop.tranche_id::text,
                   COALESCE(NULLIF(t.nom, ''), 'Tranche ' || t.numero::text) AS tranche_label,
                   t.project_id::text,
                   p.name                                                   AS project_name,
                   COALESCE(SUM(v.prix_vente), 0)                           AS total_ca,
                   COUNT(v.id)                                              AS ventes_count
            FROM vente v
            JOIN property prop ON prop.id = v.property_id
            JOIN tranche  t    ON t.id    = prop.tranche_id
            JOIN project  p    ON p.id    = t.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND prop.tranche_id IS NOT NULL
            GROUP BY prop.tranche_id, t.nom, t.numero, t.project_id, p.name
            ORDER BY p.name ASC, total_ca DESC
            """, nativeQuery = true)
    List<Object[]> salesByTranche(@Param("societeId") UUID societeId);

    /**
     * CA and vente count grouped by immeuble (via property.immeuble_id).
     * Rows: [immeubleId(text), immeubleNom(String), projectId(text), projectName(String),
     *        totalCA(BigDecimal), ventesCount(Long)].
     * Only includes ventes whose property is linked to an immeuble.
     */
    @Query(value = """
            SELECT prop.immeuble_id::text,
                   i.nom                              AS immeuble_nom,
                   i.project_id::text,
                   p.name                             AS project_name,
                   COALESCE(SUM(v.prix_vente), 0)     AS total_ca,
                   COUNT(v.id)                        AS ventes_count
            FROM vente v
            JOIN property prop ON prop.id = v.property_id
            JOIN immeuble i    ON i.id    = prop.immeuble_id
            JOIN project  p    ON p.id    = i.project_id
            WHERE v.societe_id = :societeId
              AND v.statut <> 'ANNULE'
              AND prop.immeuble_id IS NOT NULL
            GROUP BY prop.immeuble_id, i.nom, i.project_id, p.name
            ORDER BY p.name ASC, total_ca DESC
            """, nativeQuery = true)
    List<Object[]> salesByImmeuble(@Param("societeId") UUID societeId);

    /**
     * Filtered vente list for report export.
     * All parameters except societeId are optional (null = no filter).
     * Uses CAST pattern for nullable LocalDateTime params to avoid PostgreSQL type-inference errors.
     */
    @Query("""
            SELECT v FROM Vente v
            WHERE v.societeId = :societeId
              AND (:statut IS NULL OR v.statut = :statut)
              AND (CAST(:fromDt AS LocalDateTime) IS NULL OR v.createdAt >= :fromDt)
              AND (CAST(:toDt AS LocalDateTime) IS NULL OR v.createdAt <= :toDt)
            ORDER BY v.createdAt DESC
            """)
    List<Vente> findForReport(@Param("societeId") UUID societeId,
                              @Param("statut") VenteStatut statut,
                              @Param("fromDt") java.time.LocalDateTime fromDt,
                              @Param("toDt") java.time.LocalDateTime toDt);

    /**
     * Agent leaderboard for report export — no top-N limit, date-filtered.
     * Rows: [agentId(UUID), prenom(String), nomFamille(String), totalCA(BigDecimal), ventesCount(Long)].
     */
    @Query("""
            SELECT v.agent.id, v.agent.prenom, v.agent.nomFamille,
                   COALESCE(SUM(v.prixVente), 0), COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut <> com.yem.hlm.backend.vente.domain.VenteStatut.ANNULE
              AND (CAST(:fromDt AS LocalDateTime) IS NULL OR v.createdAt >= :fromDt)
              AND (CAST(:toDt AS LocalDateTime) IS NULL OR v.createdAt <= :toDt)
            GROUP BY v.agent.id, v.agent.prenom, v.agent.nomFamille
            ORDER BY SUM(v.prixVente) DESC
            """)
    List<Object[]> agentsLeaderboardForReport(@Param("societeId") UUID societeId,
                                              @Param("fromDt") java.time.LocalDateTime fromDt,
                                              @Param("toDt") java.time.LocalDateTime toDt);
}
