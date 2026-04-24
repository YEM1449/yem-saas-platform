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
     *
     * Uses an explicit LEFT JOIN for the nullable immeuble association so that
     * properties without a building assigned are never silently excluded.
     * An implicit path navigation (p.immeuble.id) would generate an INNER JOIN
     * and drop all un-assigned properties from every result set.
     */
    @Query("SELECT p FROM Property p LEFT JOIN p.immeuble imm " +
           "WHERE p.societeId = :societeId AND p.deletedAt IS NULL " +
           "AND (:projectId IS NULL OR p.project.id = :projectId) " +
           "AND (:immeubleId IS NULL OR imm.id = :immeubleId) " +
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

    /**
     * Count of non-deleted properties grouped by (type, status).
     * Rows: [type(String), status(String), count(Long)].
     * Used to build per-type absorption KPIs without joining ventes.
     */
    @Query("""
            SELECT p.type, p.status, COUNT(p)
            FROM Property p
            WHERE p.societeId = :societeId AND p.deletedAt IS NULL
            GROUP BY p.type, p.status
            ORDER BY p.type ASC, p.status ASC
            """)
    List<Object[]> inventoryByTypeAndStatus(@Param("societeId") UUID societeId);

    @Query("""
            SELECT p.project.id, p.project.name, p.status, COUNT(p), COALESCE(SUM(p.price), 0)
            FROM Property p
            WHERE p.societeId = :societeId AND p.deletedAt IS NULL
            GROUP BY p.project.id, p.project.name, p.status
            ORDER BY p.project.name ASC
            """)
    List<Object[]> inventoryByProjectStatusWithValues(@Param("societeId") UUID societeId);

    // =========================================================================
    // KPI — per-project and per-immeuble inventory breakdown
    // =========================================================================

    /**
     * Returns rows of (projectId, projectName, status, count) so callers can
     * compute per-project KPIs (total, sold, reserved, available) in one query.
     */
    @Query("""
            SELECT p.project.id, p.project.name, p.status, COUNT(p)
            FROM Property p
            WHERE p.societeId = :societeId
              AND p.deletedAt IS NULL
            GROUP BY p.project.id, p.project.name, p.status
            ORDER BY p.project.name ASC
            """)
    List<Object[]> inventoryByProjectAndStatus(@Param("societeId") UUID societeId);

    /**
     * Returns rows of (immeubleId, immeubleNom, projectId, projectName, status, count)
     * so callers can compute per-building KPIs. Only includes properties with an immeuble set.
     */
    @Query("""
            SELECT p.immeuble.id, p.immeuble.nom, p.project.id, p.project.name, p.status, COUNT(p)
            FROM Property p
            WHERE p.societeId = :societeId
              AND p.deletedAt IS NULL
              AND p.immeuble IS NOT NULL
            GROUP BY p.immeuble.id, p.immeuble.nom, p.project.id, p.project.name, p.status
            ORDER BY p.project.name ASC, p.immeuble.nom ASC
            """)
    List<Object[]> inventoryByImmeubleAndStatus(@Param("societeId") UUID societeId);

    // =========================================================================
    // KPI — per-tranche counts (used by KpiComputationService)
    // =========================================================================

    long countBySocieteIdAndTrancheIdAndDeletedAtIsNull(UUID societeId, UUID trancheId);

    @Query("""
            SELECT COUNT(p) FROM Property p
            WHERE p.societeId  = :societeId
              AND p.trancheId  = :trancheId
              AND p.status    IN :statuses
              AND p.deletedAt IS NULL
            """)
    long countBySocieteIdAndTrancheIdAndStatusIn(
            @Param("societeId")  UUID societeId,
            @Param("trancheId")  UUID trancheId,
            @Param("statuses")   java.util.List<com.yem.hlm.backend.property.domain.PropertyStatus> statuses);

    // =========================================================================
    // Sales intelligence — inventory analytics
    // =========================================================================

    /**
     * Total unsold inventory value (ACTIVE + RESERVED) and total portfolio value.
     * Returns a single-row list. Rows: [unsoldValue(BigDecimal), activeCount(Long),
     * reservedCount(Long), portfolioValue(BigDecimal), avgListPriceActive(BigDecimal)].
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN status IN ('ACTIVE','RESERVED') THEN price ELSE 0 END), 0),
                COUNT(CASE WHEN status = 'ACTIVE'   THEN 1 END),
                COUNT(CASE WHEN status = 'RESERVED' THEN 1 END),
                COALESCE(SUM(CASE WHEN status NOT IN ('DRAFT') THEN price ELSE 0 END), 0),
                COALESCE(AVG(CASE WHEN status = 'ACTIVE' THEN price END), 0)
            FROM property
            WHERE societe_id = :societeId
              AND deleted_at IS NULL
              AND price IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> inventoryValueSummary(@Param("societeId") UUID societeId);

    /**
     * Inventory aging for ACTIVE properties: how long since created_at (proxy for listing date).
     * Rows: [bucket(String), count(Long), totalValue(BigDecimal)].
     * Buckets: FRESH (≤30d), SHORT (31-90d), MEDIUM (91-180d), LONG (181-365d), STALE (>365d).
     */
    @Query(value = """
            SELECT bucket, COUNT(*), COALESCE(SUM(price),0) AS total_value
            FROM (
                SELECT price,
                       CASE
                           WHEN CURRENT_DATE - created_at::date <= 30   THEN 'FRESH'
                           WHEN CURRENT_DATE - created_at::date <= 90   THEN 'SHORT'
                           WHEN CURRENT_DATE - created_at::date <= 180  THEN 'MEDIUM'
                           WHEN CURRENT_DATE - created_at::date <= 365  THEN 'LONG'
                           ELSE 'STALE'
                       END AS bucket
                FROM property
                WHERE societe_id = :societeId
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
            ) sub
            GROUP BY bucket
            ORDER BY MIN(CURRENT_DATE - (SELECT created_at::date FROM property
                         WHERE societe_id = :societeId AND status='ACTIVE'
                         AND deleted_at IS NULL LIMIT 1))
            """, nativeQuery = true)
    List<Object[]> inventoryAgingBuckets(@Param("societeId") UUID societeId);

    /**
     * Average price per sqm by property type (for non-DRAFT, non-deleted with surface).
     * Rows: [type(String), avgPricePerSqm(Double), minPrice(BigDecimal), maxPrice(BigDecimal), count(Long)].
     */
    @Query(value = """
            SELECT type::text,
                   AVG(price / surface_area_sqm)   AS avg_price_sqm,
                   MIN(price)                       AS min_price,
                   MAX(price)                       AS max_price,
                   COUNT(*)                         AS cnt
            FROM property
            WHERE societe_id = :societeId
              AND status NOT IN ('DRAFT')
              AND deleted_at IS NULL
              AND price IS NOT NULL AND price > 0
              AND surface_area_sqm IS NOT NULL AND surface_area_sqm > 0
            GROUP BY type
            ORDER BY avg_price_sqm DESC
            """, nativeQuery = true)
    List<Object[]> avgPricePerSqmByType(@Param("societeId") UUID societeId);
}
