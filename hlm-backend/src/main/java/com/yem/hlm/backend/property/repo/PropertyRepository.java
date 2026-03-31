package com.yem.hlm.backend.property.repo;

import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Property entities with société-scoped queries and dashboard aggregations.
 * <p>
 * All queries MUST include societe_id filtering for multi-tenant isolation.
 */
@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    // ===== Standard CRUD Queries (Société-Scoped) =====

    Optional<Property> findBySocieteIdAndId(UUID societeId, UUID propertyId);

    Optional<Property> findBySocieteIdAndIdAndDeletedAtIsNull(UUID societeId, UUID propertyId);

    List<Property> findBySocieteIdAndDeletedAtIsNull(UUID societeId);

    List<Property> findBySocieteIdAndStatusAndDeletedAtIsNull(UUID societeId, PropertyStatus status);

    List<Property> findBySocieteIdAndTypeAndDeletedAtIsNull(UUID societeId, PropertyType type);

    List<Property> findBySocieteIdAndTypeAndStatusAndDeletedAtIsNull(UUID societeId, PropertyType type, PropertyStatus status);

    Optional<Property> findBySocieteIdAndReferenceCode(UUID societeId, String referenceCode);

    boolean existsBySocieteIdAndReferenceCode(UUID societeId, String referenceCode);

    /**
     * Find property with pessimistic write lock (SELECT ... FOR UPDATE).
     * Used by DepositService to atomically check + reserve a property.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Property p WHERE p.societeId = :societeId AND p.id = :propertyId AND p.deletedAt IS NULL")
    Optional<Property> findBySocieteIdAndIdForUpdate(@Param("societeId") UUID societeId, @Param("propertyId") UUID propertyId);

    // ===== Search Queries =====

    List<Property> findBySocieteIdAndCityContainingIgnoreCaseAndDeletedAtIsNull(UUID societeId, String city);

    /**
     * Flexible filter query supporting optional projectId, immeubleId, type, and status.
     */
    @Query("SELECT p FROM Property p WHERE p.societeId = :societeId AND p.deletedAt IS NULL " +
           "AND (:projectId IS NULL OR p.project.id = :projectId) " +
           "AND (:immeubleId IS NULL OR p.immeuble.id = :immeubleId) " +
           "AND (:type IS NULL OR p.type = :type) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    List<Property> findWithFilters(@Param("societeId") UUID societeId,
                                   @Param("projectId") UUID projectId,
                                   @Param("immeubleId") UUID immeubleId,
                                   @Param("type") PropertyType type,
                                   @Param("status") PropertyStatus status);

    // ===== Dashboard Aggregation Queries =====

    @Query("SELECT p.status, COUNT(p) FROM Property p " +
           "WHERE p.societeId = :societeId AND p.deletedAt IS NULL " +
           "GROUP BY p.status")
    List<Object[]> countByStatus(@Param("societeId") UUID societeId);

    @Query("SELECT p.type, COUNT(p) FROM Property p " +
           "WHERE p.societeId = :societeId AND p.deletedAt IS NULL " +
           "GROUP BY p.type")
    List<Object[]> countByType(@Param("societeId") UUID societeId);

    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.societeId = :societeId " +
           "AND p.createdAt BETWEEN :from AND :to")
    long countCreatedInPeriod(
            @Param("societeId") UUID societeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.societeId = :societeId " +
           "AND p.soldAt BETWEEN :from AND :to")
    long countSoldInPeriod(
            @Param("societeId") UUID societeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT p.type, AVG(p.price) FROM Property p " +
           "WHERE p.societeId = :societeId " +
           "AND p.deletedAt IS NULL " +
           "AND p.price IS NOT NULL " +
           "GROUP BY p.type")
    List<Object[]> averagePriceByType(@Param("societeId") UUID societeId);

    @Query("SELECT COALESCE(SUM(p.price), 0) FROM Property p " +
           "WHERE p.societeId = :societeId " +
           "AND p.soldAt BETWEEN :from AND :to " +
           "AND p.price IS NOT NULL")
    Long totalValueSoldInPeriod(
            @Param("societeId") UUID societeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // =========================================================================
    // Sales KPI Queries
    // =========================================================================

    @Query("""
            SELECT d.agent.id, d.agent.email, p.project.name, COUNT(d.id), COALESCE(SUM(p.price), 0)
            FROM com.yem.hlm.backend.deposit.domain.Deposit d,
                 Property p
            WHERE d.propertyId = p.id
              AND d.societeId = :societeId
              AND p.societeId = :societeId
              AND d.status = :confirmedStatus
              AND d.confirmedAt BETWEEN :from AND :to
            GROUP BY d.agent.id, d.agent.email, p.project.name
            ORDER BY p.project.name ASC, COUNT(d.id) DESC
            """)
    List<Object[]> salesByProjectAndAgent(
            @Param("societeId") UUID societeId,
            @Param("confirmedStatus") DepositStatus confirmedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT p.buildingName, COUNT(d.id), COALESCE(SUM(p.price), 0)
            FROM com.yem.hlm.backend.deposit.domain.Deposit d,
                 Property p
            WHERE d.propertyId = p.id
              AND d.societeId = :societeId
              AND p.societeId = :societeId
              AND d.status = :confirmedStatus
              AND d.confirmedAt BETWEEN :from AND :to
            GROUP BY p.buildingName
            ORDER BY COUNT(d.id) DESC
            """)
    List<Object[]> salesByBuilding(
            @Param("societeId") UUID societeId,
            @Param("confirmedStatus") DepositStatus confirmedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // =========================================================================
    // Commercial dashboard inventory queries
    // =========================================================================

    @Query("""
            SELECT p.status, COUNT(p)
            FROM Property p
            WHERE p.societeId  = :societeId
              AND p.deletedAt IS NULL
              AND (:projectId IS NULL OR p.project.id = :projectId)
            GROUP BY p.status
            """)
    List<Object[]> inventoryByStatus(
            @Param("societeId")  UUID societeId,
            @Param("projectId") UUID projectId
    );

    @Query("""
            SELECT p.type, COUNT(p)
            FROM Property p
            WHERE p.societeId  = :societeId
              AND p.deletedAt IS NULL
              AND (:projectId IS NULL OR p.project.id = :projectId)
            GROUP BY p.type
            """)
    List<Object[]> inventoryByType(
            @Param("societeId")  UUID societeId,
            @Param("projectId") UUID projectId
    );

    long countBySocieteIdAndDeletedAtIsNull(UUID societeId);
}
