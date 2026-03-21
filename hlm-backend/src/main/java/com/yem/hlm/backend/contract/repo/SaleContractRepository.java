package com.yem.hlm.backend.contract.repo;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleContractRepository extends JpaRepository<SaleContract, UUID>, JpaSpecificationExecutor<SaleContract> {

    /** Société-scoped lookup — returns 404 if société boundary is crossed. */
    Optional<SaleContract> findBySocieteIdAndId(UUID societeId, UUID id);

    /**
     * Loads all contracts for a given buyer contact (portal use-case).
     * JOIN FETCHes project and property to avoid N+1 in portal list view.
     */
    @Query("""
            SELECT c FROM SaleContract c
            JOIN FETCH c.project
            JOIN FETCH c.property
            WHERE c.societeId = :societeId AND c.buyerContact.id = :contactId
            ORDER BY c.createdAt DESC
            """)
    List<SaleContract> findPortalContracts(@Param("societeId") UUID societeId,
                                           @Param("contactId") UUID contactId);

    /**
     * Checks if a contact has any contract for the given property (portal ownership guard).
     */
    boolean existsBySocieteIdAndProperty_IdAndBuyerContact_Id(
            UUID societeId, UUID propertyId, UUID buyerContactId);

    /**
     * Loads a contract with all associations needed for PDF generation.
     * JOIN FETCHes buyerContact, agent, project, and property to avoid N+1.
     */
    @Query("""
            SELECT c FROM SaleContract c
            JOIN FETCH c.buyerContact
            JOIN FETCH c.agent
            JOIN FETCH c.project
            JOIN FETCH c.property
            WHERE c.societeId = :societeId AND c.id = :id
            """)
    Optional<SaleContract> findForPdf(@Param("societeId") UUID societeId, @Param("id") UUID id);

    /**
     * Checks whether an active SIGNED contract already exists for a property.
     */
    boolean existsBySocieteIdAndProperty_IdAndStatusAndCanceledAtIsNull(
            UUID societeId, UUID propertyId, SaleContractStatus status);

    /**
     * Filtered list for GET /api/contracts.
     */
    @Query("""
            SELECT c FROM SaleContract c
            WHERE c.societeId = :societeId
              AND (:status   IS NULL OR c.status       = :status)
              AND (:projectId IS NULL OR c.project.id  = :projectId)
              AND (:agentId   IS NULL OR c.agent.id    = :agentId)
              AND (CAST(:from AS LocalDateTime) IS NULL OR c.signedAt >= :from)
              AND (CAST(:to   AS LocalDateTime) IS NULL OR c.signedAt <= :to)
            ORDER BY c.createdAt DESC
            """)
    List<SaleContract> filter(
            @Param("societeId")  UUID societeId,
            @Param("status")    SaleContractStatus status,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to
    );

    // =========================================================================
    // Dashboard aggregate queries (no entity hydration — DTO projections only)
    // =========================================================================

    @Query("""
            SELECT COUNT(c), COALESCE(SUM(c.agreedPrice), 0), COALESCE(AVG(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.societeId  = :societeId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            """)
    List<Object[]> salesTotals(
            @Param("societeId")  UUID societeId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId
    );

    @Query("""
            SELECT c.project.id, c.project.name, COUNT(c), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.societeId = :societeId
              AND c.status    = 'SIGNED'
              AND c.signedAt >= :from
              AND c.signedAt <= :to
              AND (:agentId  IS NULL OR c.agent.id = :agentId)
            GROUP BY c.project.id, c.project.name
            ORDER BY COUNT(c) DESC
            """)
    List<Object[]> salesByProject(
            @Param("societeId") UUID societeId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId,
            Pageable pageable
    );

    @Query("""
            SELECT c.agent.id, c.agent.email, COUNT(c), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.societeId  = :societeId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
            GROUP BY c.agent.id, c.agent.email
            ORDER BY COUNT(c) DESC
            """)
    List<Object[]> salesByAgent(
            @Param("societeId")  UUID societeId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            Pageable pageable
    );

    @Query("""
            SELECT cast(c.signedAt as LocalDate), COALESCE(SUM(c.agreedPrice), 0)
            FROM SaleContract c
            WHERE c.societeId  = :societeId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            GROUP BY cast(c.signedAt as LocalDate)
            ORDER BY cast(c.signedAt as LocalDate)
            """)
    List<Object[]> salesAmountByDay(
            @Param("societeId")  UUID societeId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId
    );

    @Query("""
            SELECT c.signedAt, d.confirmedAt
            FROM SaleContract c, com.yem.hlm.backend.deposit.domain.Deposit d
            WHERE d.id               = c.sourceDepositId
              AND c.sourceDepositId IS NOT NULL
              AND c.societeId        = :societeId
              AND c.status           = 'SIGNED'
              AND c.signedAt        >= :from
              AND c.signedAt        <= :to
              AND (:agentId         IS NULL OR c.agent.id = :agentId)
            """)
    List<Object[]> cycleTimePairs(
            @Param("societeId") UUID societeId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    @Query(
        value = """
            SELECT c.id, c.signedAt, c.project.name, c.property.referenceCode,
                   CONCAT(c.buyerContact.firstName, ' ', c.buyerContact.lastName),
                   c.agent.email, c.agreedPrice
            FROM SaleContract c
            WHERE c.societeId  = :societeId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            ORDER BY c.signedAt DESC
            """,
        countQuery = """
            SELECT COUNT(c) FROM SaleContract c
            WHERE c.societeId  = :societeId
              AND c.status     = 'SIGNED'
              AND c.signedAt  >= :from
              AND c.signedAt  <= :to
              AND (:projectId IS NULL OR c.project.id = :projectId)
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
            """
    )
    Page<Object[]> salesForTable(
            @Param("societeId")  UUID societeId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId,
            Pageable pageable
    );

    // =========================================================================
    // Discount analytics queries (F3.2)
    // =========================================================================

    @Query("""
            SELECT AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100),
                   MAX((c.listPrice - c.agreedPrice) / c.listPrice * 100)
            FROM SaleContract c
            WHERE c.societeId = :societeId
              AND c.status    = 'SIGNED'
              AND c.listPrice IS NOT NULL
              AND c.listPrice > 0
              AND (:agentId   IS NULL OR c.agent.id   = :agentId)
              AND (:projectId IS NULL OR c.project.id = :projectId)
            """)
    List<Object[]> discountTotals(
            @Param("societeId")  UUID societeId,
            @Param("agentId")   UUID agentId,
            @Param("projectId") UUID projectId
    );

    @Query("""
            SELECT c.agent.id, c.agent.email,
                   AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100),
                   COUNT(c)
            FROM SaleContract c
            WHERE c.societeId = :societeId
              AND c.status    = 'SIGNED'
              AND c.listPrice IS NOT NULL
              AND c.listPrice > 0
            GROUP BY c.agent.id, c.agent.email
            ORDER BY AVG((c.listPrice - c.agreedPrice) / c.listPrice * 100) DESC
            """)
    List<Object[]> discountByAgent(
            @Param("societeId") UUID societeId,
            Pageable pageable
    );
}
