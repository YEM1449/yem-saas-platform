package com.yem.hlm.backend.contract.repo;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleContractRepository extends JpaRepository<SaleContract, UUID> {

    /** Tenant-scoped lookup — returns 404 if tenant boundary is crossed. */
    Optional<SaleContract> findByTenant_IdAndId(UUID tenantId, UUID id);

    /**
     * Loads all contracts for a given buyer contact (portal use-case).
     * JOIN FETCHes project and property to avoid N+1 in portal list view.
     */
    @Query("""
            SELECT c FROM SaleContract c
            JOIN FETCH c.project
            JOIN FETCH c.property
            WHERE c.tenant.id = :tenantId AND c.buyerContact.id = :contactId
            ORDER BY c.createdAt DESC
            """)
    List<SaleContract> findPortalContracts(@Param("tenantId") UUID tenantId,
                                           @Param("contactId") UUID contactId);

    /**
     * Checks if a contact has any contract for the given property (portal ownership guard).
     */
    boolean existsByTenant_IdAndProperty_IdAndBuyerContact_Id(
            UUID tenantId, UUID propertyId, UUID buyerContactId);

    /**
     * Loads a contract with all associations needed for PDF generation.
     * JOIN FETCHes tenant, buyerContact, agent, project, and property to avoid N+1.
     */
    @Query("""
            SELECT c FROM SaleContract c
            JOIN FETCH c.tenant
            JOIN FETCH c.buyerContact
            JOIN FETCH c.agent
            JOIN FETCH c.project
            JOIN FETCH c.property
            WHERE c.tenant.id = :tenantId AND c.id = :id
            """)
    Optional<SaleContract> findForPdf(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Checks whether an active SIGNED contract already exists for a property.
     * Used as a service-layer guard before attempting to sign; the DB partial unique
     * index {@code uk_sc_property_signed} is the race-condition safety net.
     * <p>
     * Predicate: {@code status = SIGNED AND canceledAt IS NULL}.
     * Invariant: cancel() always sets both {@code status = CANCELED} and {@code canceledAt != null},
     * so a SIGNED contract will never have canceledAt set. The {@code canceledAtIsNull} predicate
     * matches the DB partial unique index definition ({@code WHERE status = 'SIGNED' AND canceled_at IS NULL}),
     * keeping service-layer guard and DB constraint semantically identical.
     */
    boolean existsByTenant_IdAndProperty_IdAndStatusAndCanceledAtIsNull(
            UUID tenantId, UUID propertyId, SaleContractStatus status);

    /**
     * Filtered list for GET /api/contracts.
     * All parameters are optional (null = no filter).
     * Results are ordered by createdAt DESC.
     */
    @Query("""
            SELECT c FROM SaleContract c
            WHERE c.tenant.id = :tenantId
              AND (:status   IS NULL OR c.status       = :status)
              AND (:projectId IS NULL OR c.project.id  = :projectId)
              AND (:agentId   IS NULL OR c.agent.id    = :agentId)
              AND (CAST(:from AS LocalDateTime) IS NULL OR c.signedAt >= :from)
              AND (CAST(:to   AS LocalDateTime) IS NULL OR c.signedAt <= :to)
            ORDER BY c.createdAt DESC
            """)
    List<SaleContract> filter(
            @Param("tenantId")  UUID tenantId,
            @Param("status")    SaleContractStatus status,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to
    );

    // =========================================================================
    // Dashboard aggregate queries (no entity hydration — DTO projections only)
    // =========================================================================

    /**
     * Totals for SIGNED contracts in the given date range.
     * Returns one Object[] row: [count(Long), sum(BigDecimal), avg(BigDecimal)].
     */
    @Query("""
            SELECT COUNT(c), COALESCE(SUM(c.agreedPrice), 0), COALESCE(AVG(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.tenant.id  = :tenantId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            """)
    List<Object[]> salesTotals(
            @Param("tenantId")  UUID tenantId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId
    );

    /**
     * Top-N sales grouped by project (pass {@code Pageable.ofSize(10)}).
     * Returns rows: [projectId(UUID), projectName(String), count(Long), sum(BigDecimal)].
     */
    @Query("""
            SELECT c.project.id, c.project.name, COUNT(c), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.tenant.id = :tenantId
              AND c.status    = 'SIGNED'
              AND c.signedAt >= :from
              AND c.signedAt <= :to
              AND (:agentId  IS NULL OR c.agent.id = :agentId)
            GROUP BY c.project.id, c.project.name
            ORDER BY COUNT(c) DESC
            """)
    List<Object[]> salesByProject(
            @Param("tenantId") UUID tenantId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId,
            Pageable pageable
    );

    /**
     * Top-N sales grouped by agent (pass {@code Pageable.ofSize(10)}).
     * Returns rows: [agentId(UUID), agentEmail(String), count(Long), sum(BigDecimal)].
     */
    @Query("""
            SELECT c.agent.id, c.agent.email, COUNT(c), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.tenant.id  = :tenantId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
            GROUP BY c.agent.id, c.agent.email
            ORDER BY COUNT(c) DESC
            """)
    List<Object[]> salesByAgent(
            @Param("tenantId")  UUID tenantId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            Pageable pageable
    );

    /**
     * Daily sales amounts (trend chart). Groups SIGNED contracts by calendar day of signedAt.
     * Returns rows: [date(LocalDate), amount(BigDecimal)], ordered by date ASC.
     */
    @Query("""
            SELECT cast(c.signedAt as LocalDate), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.tenant.id  = :tenantId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            GROUP BY cast(c.signedAt as LocalDate)
            ORDER BY cast(c.signedAt as LocalDate)
            """)
    List<Object[]> salesAmountByDay(
            @Param("tenantId")  UUID tenantId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId
    );

    /**
     * Returns (signedAt, deposit.confirmedAt) pairs for contracts that originated
     * from a confirmed deposit. Used to compute avgDaysDepositToSale in the service.
     * Returns rows: [signedAt(LocalDateTime), confirmedAt(LocalDateTime)].
     */
    @Query("""
            SELECT c.signedAt, d.confirmedAt
            FROM SaleContract c, com.yem.hlm.backend.deposit.domain.Deposit d
            WHERE d.id               = c.sourceDepositId
              AND c.sourceDepositId IS NOT NULL
              AND c.tenant.id        = :tenantId
              AND c.status           = 'SIGNED'
              AND c.signedAt        >= :from
              AND c.signedAt        <= :to
              AND (:agentId         IS NULL OR c.agent.id = :agentId)
            """)
    List<Object[]> cycleTimePairs(
            @Param("tenantId") UUID tenantId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    /**
     * Paged signed contracts for the drill-down sales table.
     * Returns rows: [id, signedAt, projectName, propertyRef, buyerName, agentEmail, amount].
     */
    @Query(
        value = """
            SELECT c.id, c.signedAt, c.project.name, c.property.referenceCode,
                   CONCAT(c.buyerContact.firstName, ' ', c.buyerContact.lastName),
                   c.agent.email, c.agreedPrice
            FROM SaleContract c
            WHERE c.tenant.id  = :tenantId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            ORDER BY c.signedAt DESC
            """,
        countQuery = """
            SELECT COUNT(c) FROM SaleContract c
            WHERE c.tenant.id  = :tenantId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            """
    )
    Page<Object[]> salesForTable(
            @Param("tenantId")  UUID tenantId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId,
            Pageable pageable
    );

    // =========================================================================
    // Discount analytics queries (F3.2)
    // =========================================================================

    /**
     * Avg and max discount percent for SIGNED contracts where listPrice IS NOT NULL.
     * Discount % = (listPrice - agreedPrice) / listPrice * 100.
     * Returns one Object[] row: [avgDiscountPct(BigDecimal), maxDiscountPct(BigDecimal)].
     */
    @Query("""
            SELECT AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100),
                   MAX((c.listPrice - c.agreedPrice) / c.listPrice * 100)
            FROM SaleContract c
            WHERE c.tenant.id = :tenantId
              AND c.status    = 'SIGNED'
              AND c.listPrice IS NOT NULL
              AND c.listPrice > 0
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
              AND (:projectId IS NULL OR c.project.id = :projectId)
            """)
    List<Object[]> discountTotals(
            @Param("tenantId")  UUID tenantId,
            @Param("agentId")   UUID agentId,
            @Param("projectId") UUID projectId
    );

    /**
     * Top-N agents by average discount percent, for SIGNED contracts with listPrice set.
     * Returns rows: [agentId(UUID), agentEmail(String), avgDiscountPct(BigDecimal), salesCount(Long)].
     */
    @Query("""
            SELECT c.agent.id, c.agent.email,
                   AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100),
                   COUNT(c)
            FROM SaleContract c
            WHERE c.tenant.id = :tenantId
              AND c.status    = 'SIGNED'
              AND c.listPrice IS NOT NULL
              AND c.listPrice > 0
            GROUP BY c.agent.id, c.agent.email
            ORDER BY AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100) DESC
            """)
    List<Object[]> discountByAgent(
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );
}
