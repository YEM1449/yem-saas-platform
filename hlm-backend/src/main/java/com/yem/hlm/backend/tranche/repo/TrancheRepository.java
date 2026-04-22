package com.yem.hlm.backend.tranche.repo;

import com.yem.hlm.backend.tranche.domain.Tranche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrancheRepository extends JpaRepository<Tranche, UUID> {

    List<Tranche> findBySocieteIdAndProjectIdOrderByNumeroAsc(UUID societeId, UUID projectId);

    Optional<Tranche> findBySocieteIdAndId(UUID societeId, UUID id);

    boolean existsBySocieteIdAndProjectIdAndNumero(UUID societeId, UUID projectId, Integer numero);

    /** Count buildings (Immeubles) belonging to this tranche. */
    @Query("SELECT COUNT(i) FROM Immeuble i WHERE i.societeId = :societeId AND i.trancheId = :trancheId")
    int countBuildings(@Param("societeId") UUID societeId, @Param("trancheId") UUID trancheId);

    /** Count property units belonging to this tranche. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.societeId = :societeId AND p.trancheId = :trancheId AND p.deletedAt IS NULL")
    int countUnits(@Param("societeId") UUID societeId, @Param("trancheId") UUID trancheId);

    /** Count units by status for KPI. */
    @Query("SELECT p.status, COUNT(p) FROM Property p WHERE p.societeId = :societeId AND p.trancheId = :trancheId AND p.deletedAt IS NULL GROUP BY p.status")
    List<Object[]> countUnitsByStatus(@Param("societeId") UUID societeId, @Param("trancheId") UUID trancheId);

    /**
     * Upcoming deliveries for the owner executive view: tranches with a planned
     * delivery date in [fromDate, toDate), excluding those already LIVREE.
     * Joins project to return its name, and aggregates total / sold units.
     * <p>Rows: [trancheId UUID, trancheNom String, trancheNumero Integer,
     *          projectId UUID, projectName String, dateLivraisonPrevue Date,
     *          totalUnits Long, soldUnits Long] ordered by delivery date ascending.
     */
    @Query(value = """
            SELECT t.id,
                   t.nom,
                   t.numero,
                   t.project_id,
                   pr.name,
                   t.date_livraison_prevue,
                   COALESCE(u.total_units, 0),
                   COALESCE(u.sold_units, 0)
            FROM tranche t
            JOIN project pr ON pr.id = t.project_id
            LEFT JOIN (
                SELECT tranche_id,
                       COUNT(*) AS total_units,
                       COUNT(*) FILTER (WHERE status = 'SOLD') AS sold_units
                FROM property
                WHERE societe_id = :societeId
                  AND tranche_id IS NOT NULL
                  AND deleted_at IS NULL
                GROUP BY tranche_id
            ) u ON u.tranche_id = t.id
            WHERE t.societe_id = :societeId
              AND t.statut <> 'LIVREE'
              AND t.date_livraison_prevue IS NOT NULL
              AND t.date_livraison_prevue >= :fromDate
              AND t.date_livraison_prevue <  :toDate
            ORDER BY t.date_livraison_prevue ASC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findUpcomingDeliveries(@Param("societeId") UUID societeId,
                                          @Param("fromDate") java.time.LocalDate fromDate,
                                          @Param("toDate") java.time.LocalDate toDate);

    /**
     * Returns the earliest planned delivery date per project.
     * Row: [projectId UUID, minDateLivraisonPrevue Date].
     * Used by Project Director KPI tab.
     */
    @Query(value = """
            SELECT t.project_id, MIN(t.date_livraison_prevue)
            FROM tranche t
            WHERE t.societe_id = :societeId
              AND t.date_livraison_prevue IS NOT NULL
            GROUP BY t.project_id
            """, nativeQuery = true)
    List<Object[]> findEarliestDeliveryPerProject(@Param("societeId") UUID societeId);
}
