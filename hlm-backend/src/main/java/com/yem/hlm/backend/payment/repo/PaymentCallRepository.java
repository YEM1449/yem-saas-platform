package com.yem.hlm.backend.payment.repo;

import com.yem.hlm.backend.payment.domain.PaymentCall;
import com.yem.hlm.backend.payment.domain.PaymentCallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCallRepository extends JpaRepository<PaymentCall, UUID> {

    /** Tenant-scoped lookup. */
    Optional<PaymentCall> findByTenant_IdAndId(UUID tenantId, UUID id);

    /** List all calls for a tranche, ordered by call_number. */
    List<PaymentCall> findByTenant_IdAndTranche_IdOrderByCallNumberAsc(UUID tenantId, UUID trancheId);

    /** Paged list of all calls in a tenant, most-recent-issued first. */
    Page<PaymentCall> findByTenant_Id(UUID tenantId, Pageable pageable);

    /**
     * ISSUED calls whose due_date is in the past and not yet OVERDUE or CLOSED.
     * Used by the overdue scheduler.
     */
    @Query("""
            SELECT pc FROM PaymentCall pc
            WHERE pc.tenant.id = :tenantId
              AND pc.status = com.yem.hlm.backend.payment.domain.PaymentCallStatus.ISSUED
              AND pc.dueDate IS NOT NULL AND pc.dueDate < :today
            """)
    List<PaymentCall> findOverdueCalls(@Param("tenantId") UUID tenantId,
                                       @Param("today") LocalDate today);

    /** Tenant IDs with at least one ISSUED call (used by the scheduler to limit work). */
    @Query("SELECT DISTINCT pc.tenant.id FROM PaymentCall pc WHERE pc.status = " +
           "com.yem.hlm.backend.payment.domain.PaymentCallStatus.ISSUED")
    List<UUID> findTenantsWithIssuedCalls();

    /** OVERDUE payment calls for a tenant — used by reminder service for notifications. */
    @Query("""
            SELECT pc FROM PaymentCall pc
            JOIN FETCH pc.tenant
            JOIN FETCH pc.tranche t
            JOIN FETCH t.schedule s
            JOIN FETCH s.saleContract sc
            JOIN FETCH sc.agent
            WHERE pc.tenant.id = :tenantId
              AND pc.status = com.yem.hlm.backend.payment.domain.PaymentCallStatus.OVERDUE
            """)
    List<PaymentCall> findOverdueCallsWithAgent(@Param("tenantId") UUID tenantId);

    /** Distinct tenant IDs with at least one OVERDUE call. */
    @Query("SELECT DISTINCT pc.tenant.id FROM PaymentCall pc WHERE pc.status = " +
           "com.yem.hlm.backend.payment.domain.PaymentCallStatus.OVERDUE")
    List<UUID> findTenantsWithOverdueCalls();

    /**
     * Loads a call with its tranche, schedule, and contract (JOIN FETCH) for PDF generation.
     */
    @Query("""
            SELECT pc FROM PaymentCall pc
            JOIN FETCH pc.tenant
            JOIN FETCH pc.tranche t
            JOIN FETCH t.schedule ps
            JOIN FETCH ps.saleContract sc
            JOIN FETCH sc.tenant
            JOIN FETCH sc.buyerContact
            JOIN FETCH sc.agent
            JOIN FETCH sc.property
            WHERE pc.tenant.id = :tenantId AND pc.id = :id
            """)
    Optional<PaymentCall> findForPdf(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    // =========================================================================
    // Receivables dashboard aggregate queries (no entity hydration)
    // =========================================================================

    /**
     * Outstanding and overdue totals for the receivables dashboard.
     * Returns one Object[] row: [outstanding(BigDecimal), overdue(BigDecimal)].
     * outstanding = SUM(amountDue) for ISSUED + OVERDUE calls.
     * overdue     = SUM(amountDue) for OVERDUE calls only.
     */
    @Query("""
            SELECT COALESCE(SUM(pc.amountDue), 0),
                   COALESCE(SUM(CASE WHEN pc.status = 'OVERDUE' THEN pc.amountDue ELSE 0 END), 0)
            FROM PaymentCall pc
            JOIN pc.tranche pt
            JOIN pt.schedule ps
            JOIN ps.saleContract sc
            WHERE pc.tenant.id = :tenantId
              AND pc.status IN ('ISSUED', 'OVERDUE')
              AND (:agentId IS NULL OR sc.agent.id = :agentId)
            """)
    List<Object[]> receivablesTotals(@Param("tenantId") UUID tenantId,
                                     @Param("agentId") UUID agentId);

    /**
     * Total amountDue for all ISSUED+OVERDUE+CLOSED calls (denominator for collection rate).
     */
    @Query("""
            SELECT COALESCE(SUM(pc.amountDue), 0)
            FROM PaymentCall pc
            WHERE pc.tenant.id = :tenantId
              AND pc.status IN ('ISSUED', 'OVERDUE', 'CLOSED')
            """)
    BigDecimal totalIssuedAmount(@Param("tenantId") UUID tenantId);

    /**
     * Top-N projects by OVERDUE call amounts for the receivables dashboard.
     * Returns rows: [projectId(UUID), projectName(String), overdueAmount(BigDecimal)].
     */
    @Query("""
            SELECT sc.project.id, sc.project.name, COALESCE(SUM(pc.amountDue), 0)
            FROM PaymentCall pc
            JOIN pc.tranche pt
            JOIN pt.schedule ps
            JOIN ps.saleContract sc
            WHERE pc.tenant.id = :tenantId
              AND pc.status = 'OVERDUE'
            GROUP BY sc.project.id, sc.project.name
            ORDER BY COALESCE(SUM(pc.amountDue), 0) DESC
            """)
    List<Object[]> overdueByProject(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * ISSUED + OVERDUE calls with amountDue and dueDate — used for aging bucket computation in Java.
     * Returns rows: [amountDue(BigDecimal), dueDate(LocalDate)].
     */
    @Query("""
            SELECT pc.amountDue, pc.dueDate
            FROM PaymentCall pc
            JOIN pc.tranche pt
            JOIN pt.schedule ps
            JOIN ps.saleContract sc
            WHERE pc.tenant.id = :tenantId
              AND pc.status IN ('ISSUED', 'OVERDUE')
              AND pc.dueDate IS NOT NULL
              AND (:agentId IS NULL OR sc.agent.id = :agentId)
            """)
    List<Object[]> outstandingCallsForAging(@Param("tenantId") UUID tenantId,
                                            @Param("agentId") UUID agentId);
}
