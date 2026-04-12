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

    /** Count ventes stuck in early pipeline stages since before :before (admin stalled alert). */
    @Query("""
            SELECT COUNT(v)
            FROM Vente v
            WHERE v.societeId = :societeId
              AND v.statut IN :statuts
              AND v.createdAt < :before
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
}
