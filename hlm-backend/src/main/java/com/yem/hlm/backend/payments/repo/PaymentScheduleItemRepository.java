package com.yem.hlm.backend.payments.repo;

import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentScheduleItemRepository extends JpaRepository<PaymentScheduleItem, UUID> {

    /** All schedule items for a contract in sequence order — AGENT-agnostic (service enforces). */
    List<PaymentScheduleItem> findBySocieteIdAndContractIdOrderBySequenceAsc(
            UUID societeId, UUID contractId);

    /** Société-scoped single item lookup. */
    Optional<PaymentScheduleItem> findBySocieteIdAndId(UUID societeId, UUID id);

    /** Next sequence number for a contract (for auto-increment on create). */
    @Query("SELECT COALESCE(MAX(i.sequence), 0) FROM PaymentScheduleItem i " +
           "WHERE i.societeId = :societeId AND i.contractId = :contractId")
    int maxSequence(@Param("societeId") UUID societeId, @Param("contractId") UUID contractId);

    // ── Reminder / scheduler queries ─────────────────────────────────────────

    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.dueDate = :dueDate
          AND i.status IN ('ISSUED', 'SENT')
        """)
    List<PaymentScheduleItem> findDueOn(@Param("societeId") UUID societeId,
                                        @Param("dueDate") LocalDate dueDate);

    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.dueDate < :before
          AND i.status IN ('ISSUED', 'SENT', 'OVERDUE')
        """)
    List<PaymentScheduleItem> findOverdueCandidates(@Param("societeId") UUID societeId,
                                                    @Param("before") LocalDate before);

    /**
     * All distinct société IDs that have any ISSUED/SENT/OVERDUE items.
     * Used by the reminder scheduler to iterate sociétés without a full table scan.
     */
    @Query("""
        SELECT DISTINCT i.societeId FROM PaymentScheduleItem i
        WHERE i.status IN ('ISSUED', 'SENT', 'OVERDUE')
        """)
    List<UUID> findActiveTenantIds();

    // ── Cash dashboard aggregate queries ──────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(i.amount), 0) FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.status NOT IN ('CANCELED', 'DRAFT')
          AND i.dueDate BETWEEN :from AND :to
        """)
    BigDecimal sumExpectedInPeriod(@Param("societeId") UUID societeId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(i.amount), 0) FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.issuedAt IS NOT NULL
          AND CAST(i.issuedAt AS LocalDate) BETWEEN :from AND :to
        """)
    BigDecimal sumIssuedInPeriod(@Param("societeId") UUID societeId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    long countBySocieteIdAndStatus(UUID societeId, PaymentScheduleStatus status);

    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.status = 'OVERDUE'
          AND i.dueDate BETWEEN :from AND :to
        """)
    List<PaymentScheduleItem> findOverdueInRange(@Param("societeId") UUID societeId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.societeId = :societeId
          AND i.status IN ('ISSUED', 'SENT')
          AND i.dueDate >= :from
        ORDER BY i.dueDate ASC
        """)
    List<PaymentScheduleItem> findUpcomingDue(@Param("societeId") UUID societeId,
                                               @Param("from") LocalDate from,
                                               org.springframework.data.domain.Pageable pageable);

    // ── Receivables dashboard aggregate queries ────────────────────────────────

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN status IN ('ISSUED','SENT','OVERDUE') THEN amount ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN status = 'OVERDUE' THEN amount ELSE 0 END), 0)
        FROM payment_schedule_item
        WHERE societe_id = :societeId
        """, nativeQuery = true)
    List<Object[]> receivablesTotals(@Param("societeId") UUID societeId);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN psi.status IN ('ISSUED','SENT','OVERDUE') THEN psi.amount ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN psi.status = 'OVERDUE' THEN psi.amount ELSE 0 END), 0)
        FROM payment_schedule_item psi
        JOIN sale_contract sc ON sc.id = psi.contract_id
        WHERE psi.societe_id = :societeId
          AND sc.agent_id = :agentId
        """, nativeQuery = true)
    List<Object[]> receivablesTotalsForAgent(@Param("societeId") UUID societeId,
                                             @Param("agentId") UUID agentId);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM PaymentScheduleItem i " +
           "WHERE i.societeId = :societeId AND i.status NOT IN ('DRAFT', 'CANCELED')")
    BigDecimal totalIssuedAmount(@Param("societeId") UUID societeId);

    @Query(value = """
        SELECT amount, due_date
        FROM payment_schedule_item
        WHERE societe_id = :societeId
          AND status IN ('ISSUED','SENT','OVERDUE')
        """, nativeQuery = true)
    List<Object[]> outstandingForAging(@Param("societeId") UUID societeId);

    @Query(value = """
        SELECT psi.amount, psi.due_date
        FROM payment_schedule_item psi
        JOIN sale_contract sc ON sc.id = psi.contract_id
        WHERE psi.societe_id = :societeId
          AND psi.status IN ('ISSUED','SENT','OVERDUE')
          AND sc.agent_id = :agentId
        """, nativeQuery = true)
    List<Object[]> outstandingForAgingByAgent(@Param("societeId") UUID societeId,
                                              @Param("agentId") UUID agentId);

    @Query(value = """
        SELECT psi.project_id, p.name, COALESCE(SUM(psi.amount), 0)
        FROM payment_schedule_item psi
        JOIN project p ON p.id = psi.project_id
        WHERE psi.societe_id = :societeId
          AND psi.status = 'OVERDUE'
        GROUP BY psi.project_id, p.name
        ORDER BY SUM(psi.amount) DESC
        """, nativeQuery = true)
    List<Object[]> overdueByProject(@Param("societeId") UUID societeId,
                                    org.springframework.data.domain.Pageable pageable);
}
