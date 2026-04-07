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
}
