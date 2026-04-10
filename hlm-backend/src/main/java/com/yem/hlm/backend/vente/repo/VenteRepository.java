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
}
