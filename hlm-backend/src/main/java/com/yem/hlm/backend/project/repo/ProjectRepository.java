package com.yem.hlm.backend.project.repo;

import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Project entities with tenant-scoped queries and KPI aggregations.
 * All queries MUST include tenant_id filtering for multi-tenant isolation.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // ===== Standard CRUD Queries (Tenant-Scoped) =====

    Optional<Project> findByTenant_IdAndId(UUID tenantId, UUID projectId);

    List<Project> findByTenant_IdOrderByNameAsc(UUID tenantId);

    List<Project> findByTenant_IdAndStatusOrderByNameAsc(UUID tenantId, ProjectStatus status);

    boolean existsByTenant_IdAndName(UUID tenantId, String name);

    boolean existsByTenant_IdAndNameAndIdNot(UUID tenantId, String name, UUID excludeId);

    // ===== KPI Aggregation Queries =====

    /**
     * Count non-deleted properties for this project, grouped by status.
     * Returns rows: [PropertyStatus, Long count]
     */
    @Query("SELECT p.status, COUNT(p) FROM com.yem.hlm.backend.property.domain.Property p " +
           "WHERE p.tenant.id = :tenantId AND p.project.id = :projectId AND p.deletedAt IS NULL " +
           "GROUP BY p.status")
    List<Object[]> countPropertiesByStatus(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    /**
     * Count non-deleted properties for this project, grouped by type.
     * Returns rows: [PropertyType, Long count]
     */
    @Query("SELECT p.type, COUNT(p) FROM com.yem.hlm.backend.property.domain.Property p " +
           "WHERE p.tenant.id = :tenantId AND p.project.id = :projectId AND p.deletedAt IS NULL " +
           "GROUP BY p.type")
    List<Object[]> countPropertiesByType(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    /**
     * Total count of non-deleted properties for this project.
     */
    @Query("SELECT COUNT(p) FROM com.yem.hlm.backend.property.domain.Property p " +
           "WHERE p.tenant.id = :tenantId AND p.project.id = :projectId AND p.deletedAt IS NULL")
    long countTotalProperties(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    /**
     * Deposit count and total amount for this project (all statuses).
     * Returns a single row: [Long count, BigDecimal totalAmount]
     */
    @Query("SELECT COUNT(d.id), COALESCE(SUM(p.price), 0) " +
           "FROM com.yem.hlm.backend.deposit.domain.Deposit d, " +
           "     com.yem.hlm.backend.property.domain.Property p " +
           "WHERE d.propertyId = p.id " +
           "  AND p.project.id = :projectId " +
           "  AND p.tenant.id = :tenantId " +
           "  AND d.tenant.id = :tenantId")
    List<Object[]> depositStats(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    /**
     * Sales count and total amount for this project (CONFIRMED deposits only).
     * Returns a single row: [Long count, BigDecimal totalAmount]
     */
    @Query("SELECT COUNT(d.id), COALESCE(SUM(p.price), 0) " +
           "FROM com.yem.hlm.backend.deposit.domain.Deposit d, " +
           "     com.yem.hlm.backend.property.domain.Property p " +
           "WHERE d.propertyId = p.id " +
           "  AND p.project.id = :projectId " +
           "  AND p.tenant.id = :tenantId " +
           "  AND d.tenant.id = :tenantId " +
           "  AND d.status = :confirmedStatus")
    List<Object[]> salesStats(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("confirmedStatus") DepositStatus confirmedStatus
    );
}
