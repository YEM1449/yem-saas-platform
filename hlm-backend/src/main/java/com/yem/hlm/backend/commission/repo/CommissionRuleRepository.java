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

    /** Tenant-scoped lookup. */
    Optional<CommissionRule> findByTenant_IdAndId(UUID tenantId, UUID id);

    /** All rules for a tenant (for admin list/management). */
    List<CommissionRule> findByTenant_IdOrderByEffectiveFromDesc(UUID tenantId);

    /**
     * Project-specific rule effective on a given date (most recent effective_from first).
     * Returns the first match — caller picks the first element.
     */
    @Query("""
            SELECT r FROM CommissionRule r
            WHERE r.tenant.id   = :tenantId
              AND r.project.id  = :projectId
              AND r.effectiveFrom <= :onDate
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate)
            ORDER BY r.effectiveFrom DESC
            """)
    List<CommissionRule> findProjectRule(
            @Param("tenantId")  UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("onDate")    LocalDate onDate
    );

    /**
     * Tenant-wide default rule effective on a given date (project_id IS NULL).
     */
    @Query("""
            SELECT r FROM CommissionRule r
            WHERE r.tenant.id   = :tenantId
              AND r.project     IS NULL
              AND r.effectiveFrom <= :onDate
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate)
            ORDER BY r.effectiveFrom DESC
            """)
    List<CommissionRule> findTenantDefaultRule(
            @Param("tenantId") UUID tenantId,
            @Param("onDate")   LocalDate onDate
    );
}
