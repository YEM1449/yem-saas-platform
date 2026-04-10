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
