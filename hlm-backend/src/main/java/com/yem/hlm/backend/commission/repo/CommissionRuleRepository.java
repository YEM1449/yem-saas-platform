package com.yem.hlm.backend.commission.repo;

import com.yem.hlm.backend.commission.domain.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

    /** Société-scoped lookup. */
    Optional<CommissionRule> findBySocieteIdAndId(UUID societeId, UUID id);

    /** All rules for a société (for admin list/management). */
    List<CommissionRule> findBySocieteIdOrderByEffectiveFromDesc(UUID societeId);

    /**
     * Project-specific rule effective on a given date (most recent effective_from first).
     */
    @Query("""
            SELECT r FROM CommissionRule r
            WHERE r.societeId   = :societeId
              AND r.project.id  = :projectId
              AND r.effectiveFrom <= :onDate
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate)
            ORDER BY r.effectiveFrom DESC
            """)
    List<CommissionRule> findProjectRule(
            @Param("societeId")  UUID societeId,
            @Param("projectId") UUID projectId,
            @Param("onDate")    LocalDate onDate
    );

    /**
     * Société-wide default rule effective on a given date (project_id IS NULL).
     */
    @Query("""
            SELECT r FROM CommissionRule r
            WHERE r.societeId   = :societeId
              AND r.project     IS NULL
              AND r.effectiveFrom <= :onDate
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate)
            ORDER BY r.effectiveFrom DESC
            """)
    List<CommissionRule> findTenantDefaultRule(
            @Param("societeId") UUID societeId,
            @Param("onDate")   LocalDate onDate
    );
}
