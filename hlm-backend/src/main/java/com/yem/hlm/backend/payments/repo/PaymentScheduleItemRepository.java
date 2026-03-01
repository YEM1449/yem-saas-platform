package com.yem.hlm.backend.payments.repo;

import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentScheduleItemRepository extends JpaRepository<PaymentScheduleItem, UUID> {

    /** All schedule items for a contract in sequence order — AGENT-agnostic (service enforces). */
    List<PaymentScheduleItem> findByTenant_IdAndContractIdOrderBySequenceAsc(
            UUID tenantId, UUID contractId);

    /** Tenant-scoped single item lookup. */
    Optional<PaymentScheduleItem> findByTenant_IdAndId(UUID tenantId, UUID id);

    /** Next sequence number for a contract (for auto-increment on create). */
    @Query("SELECT COALESCE(MAX(i.sequence), 0) FROM PaymentScheduleItem i " +
           "WHERE i.tenant.id = :tenantId AND i.contractId = :contractId")
    int maxSequence(@Param("tenantId") UUID tenantId, @Param("contractId") UUID contractId);

    // ── Reminder / scheduler queries ─────────────────────────────────────────

    /**
     * Items due on exactly {@code dueDate} with status in ISSUED or SENT and remaining > 0.
     * Used by the reminder scheduler for pre-due reminders.
     */
    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.dueDate = :dueDate
          AND i.status IN ('ISSUED', 'SENT')
        """)
    List<PaymentScheduleItem> findDueOn(@Param("tenantId") UUID tenantId,
                                        @Param("dueDate") LocalDate dueDate);

    /**
     * All tenant items due before {@code before} that are still ISSUED or SENT
     * (candidates for OVERDUE marking and overdue reminders).
     */
    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.dueDate < :before
          AND i.status IN ('ISSUED', 'SENT', 'OVERDUE')
        """)
    List<PaymentScheduleItem> findOverdueCandidates(@Param("tenantId") UUID tenantId,
                                                    @Param("before") LocalDate before);

    /**
     * All distinct tenant IDs that have any ISSUED/SENT/OVERDUE items.
     * Used by the reminder scheduler to iterate tenants without a full table scan.
     */
    @Query("""
        SELECT DISTINCT i.tenant.id FROM PaymentScheduleItem i
        WHERE i.status IN ('ISSUED', 'SENT', 'OVERDUE')
        """)
    List<UUID> findActiveTenantIds();

    // ── Cash dashboard aggregate queries ──────────────────────────────────────

    /** Total planned amount for items whose due_date falls in [from, to]. */
    @Query("""
        SELECT COALESCE(SUM(i.amount), 0) FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.status NOT IN ('CANCELED', 'DRAFT')
          AND i.dueDate BETWEEN :from AND :to
        """)
    BigDecimal sumExpectedInPeriod(@Param("tenantId") UUID tenantId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /** Total planned amount for items issued in [from, to]. */
    @Query("""
        SELECT COALESCE(SUM(i.amount), 0) FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.issuedAt IS NOT NULL
          AND CAST(i.issuedAt AS LocalDate) BETWEEN :from AND :to
        """)
    BigDecimal sumIssuedInPeriod(@Param("tenantId") UUID tenantId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    /** Count of OVERDUE items for the tenant. */
    long countByTenant_IdAndStatus(UUID tenantId, PaymentScheduleStatus status);

    /**
     * Aging bucket: sum of (amount - collected) for OVERDUE items in a due_date range.
     * Collected is passed in as a correlated sub-select via native is too complex in JPQL;
     * the service fetches raw items and computes per bucket instead.
     * This query returns OVERDUE items with their amount for a given due_date window.
     */
    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.status = 'OVERDUE'
          AND i.dueDate BETWEEN :from AND :to
        """)
    List<PaymentScheduleItem> findOverdueInRange(@Param("tenantId") UUID tenantId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /**
     * Upcoming items in ISSUED or SENT status with due_date >= today, ordered by due_date ASC.
     * Used by the cash dashboard "next due" list.
     */
    @Query("""
        SELECT i FROM PaymentScheduleItem i
        WHERE i.tenant.id = :tenantId
          AND i.status IN ('ISSUED', 'SENT')
          AND i.dueDate >= :from
        ORDER BY i.dueDate ASC
        """)
    List<PaymentScheduleItem> findUpcomingDue(@Param("tenantId") UUID tenantId,
                                               @Param("from") LocalDate from,
                                               org.springframework.data.domain.Pageable pageable);
}
