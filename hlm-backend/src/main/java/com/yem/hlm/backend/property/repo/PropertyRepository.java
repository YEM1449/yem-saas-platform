package com.yem.hlm.backend.property.repo;

import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Property entities with tenant-scoped queries and dashboard aggregations.
 * <p>
 * All queries MUST include tenant_id filtering for multi-tenant isolation.
 */
@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    // ===== Standard CRUD Queries (Tenant-Scoped) =====

    /**
     * Find property by ID within a specific tenant (including soft-deleted).
     *
     * @param tenantId the tenant ID
     * @param propertyId the property ID
     * @return Optional containing the property if found
     */
    Optional<Property> findByTenant_IdAndId(UUID tenantId, UUID propertyId);

    /**
     * Find property by ID within a specific tenant (excluding soft-deleted).
     *
     * @param tenantId the tenant ID
     * @param propertyId the property ID
     * @return Optional containing the property if found and not deleted
     */
    Optional<Property> findByTenant_IdAndIdAndDeletedAtIsNull(UUID tenantId, UUID propertyId);

    /**
     * Find all non-deleted properties for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of properties
     */
    List<Property> findByTenant_IdAndDeletedAtIsNull(UUID tenantId);

    /**
     * Find properties by status (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @param status the property status
     * @return list of properties
     */
    List<Property> findByTenant_IdAndStatusAndDeletedAtIsNull(UUID tenantId, PropertyStatus status);

    /**
     * Find properties by type (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @param type the property type
     * @return list of properties
     */
    List<Property> findByTenant_IdAndTypeAndDeletedAtIsNull(UUID tenantId, PropertyType type);

    /**
     * Find properties by type and status (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @param type the property type
     * @param status the property status
     * @return list of properties
     */
    List<Property> findByTenant_IdAndTypeAndStatusAndDeletedAtIsNull(UUID tenantId, PropertyType type, PropertyStatus status);

    /**
     * Find property by reference code within a tenant.
     *
     * @param tenantId the tenant ID
     * @param referenceCode the reference code
     * @return Optional containing the property if found
     */
    Optional<Property> findByTenant_IdAndReferenceCode(UUID tenantId, String referenceCode);

    /**
     * Check if a property with the given reference code exists for this tenant.
     *
     * @param tenantId the tenant ID
     * @param referenceCode the reference code
     * @return true if exists
     */
    boolean existsByTenant_IdAndReferenceCode(UUID tenantId, String referenceCode);

    /**
     * Find property with pessimistic write lock (SELECT ... FOR UPDATE).
     * Used by DepositService to atomically check + reserve a property.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Property p WHERE p.tenant.id = :tenantId AND p.id = :propertyId AND p.deletedAt IS NULL")
    Optional<Property> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("propertyId") UUID propertyId);

    // ===== Search Queries =====

    /**
     * Find properties by city (case-insensitive, partial match, excluding deleted).
     *
     * @param tenantId the tenant ID
     * @param city the city name (partial match)
     * @return list of properties
     */
    List<Property> findByTenant_IdAndCityContainingIgnoreCaseAndDeletedAtIsNull(UUID tenantId, String city);

    // ===== Dashboard Aggregation Queries =====

    /**
     * Count properties by status (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @return list of [status, count] arrays
     */
    @Query("SELECT p.status, COUNT(p) FROM Property p " +
           "WHERE p.tenant.id = :tenantId AND p.deletedAt IS NULL " +
           "GROUP BY p.status")
    List<Object[]> countByStatus(@Param("tenantId") UUID tenantId);

    /**
     * Count properties by type (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @return list of [type, count] arrays
     */
    @Query("SELECT p.type, COUNT(p) FROM Property p " +
           "WHERE p.tenant.id = :tenantId AND p.deletedAt IS NULL " +
           "GROUP BY p.type")
    List<Object[]> countByType(@Param("tenantId") UUID tenantId);

    /**
     * Count properties created in a specific period.
     *
     * @param tenantId the tenant ID
     * @param from start of period
     * @param to end of period
     * @return count of properties created
     */
    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND p.createdAt BETWEEN :from AND :to")
    long countCreatedInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Count properties sold in a specific period.
     *
     * @param tenantId the tenant ID
     * @param from start of period
     * @param to end of period
     * @return count of properties sold
     */
    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND p.soldAt BETWEEN :from AND :to")
    long countSoldInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Calculate average price by property type (excluding deleted).
     *
     * @param tenantId the tenant ID
     * @return list of [type, average_price] arrays
     */
    @Query("SELECT p.type, AVG(p.price) FROM Property p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND p.deletedAt IS NULL " +
           "AND p.price IS NOT NULL " +
           "GROUP BY p.type")
    List<Object[]> averagePriceByType(@Param("tenantId") UUID tenantId);

    /**
     * Calculate total value of properties sold in a period.
     *
     * @param tenantId the tenant ID
     * @param from start of period
     * @param to end of period
     * @return sum of prices for sold properties
     */
    @Query("SELECT COALESCE(SUM(p.price), 0) FROM Property p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND p.soldAt BETWEEN :from AND :to " +
           "AND p.price IS NOT NULL")
    Long totalValueSoldInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ===== Sales KPI Queries =====

    /**
     * Sales per project per agent: counts CONFIRMED deposits grouped by property.projectName
     * and deposit.agent, for a given period (filtered by deposit.confirmedAt).
     * <p>
     * Returns rows: [agentId (UUID), agentEmail (String), projectName (String), count (Long), totalValue (BigDecimal)]
     */
    @Query("""
            SELECT d.agent.id, d.agent.email, p.project.name, COUNT(d.id), COALESCE(SUM(p.price), 0)
            FROM com.yem.hlm.backend.deposit.domain.Deposit d,
                 Property p
            WHERE d.propertyId = p.id
              AND d.tenant.id = :tenantId
              AND p.tenant.id = :tenantId
              AND d.status = :confirmedStatus
              AND d.confirmedAt BETWEEN :from AND :to
            GROUP BY d.agent.id, d.agent.email, p.project.name
            ORDER BY p.project.name ASC, COUNT(d.id) DESC
            """)
    List<Object[]> salesByProjectAndAgent(
            @Param("tenantId") UUID tenantId,
            @Param("confirmedStatus") DepositStatus confirmedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Sales per building: counts CONFIRMED deposits grouped by property.buildingName,
     * for a given period (filtered by deposit.confirmedAt).
     * <p>
     * Returns rows: [buildingName (String), count (Long), totalValue (BigDecimal)]
     */
    @Query("""
            SELECT p.buildingName, COUNT(d.id), COALESCE(SUM(p.price), 0)
            FROM com.yem.hlm.backend.deposit.domain.Deposit d,
                 Property p
            WHERE d.propertyId = p.id
              AND d.tenant.id = :tenantId
              AND p.tenant.id = :tenantId
              AND d.status = :confirmedStatus
              AND d.confirmedAt BETWEEN :from AND :to
            GROUP BY p.buildingName
            ORDER BY COUNT(d.id) DESC
            """)
    List<Object[]> salesByBuilding(
            @Param("tenantId") UUID tenantId,
            @Param("confirmedStatus") DepositStatus confirmedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
