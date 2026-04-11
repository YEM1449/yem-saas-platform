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
