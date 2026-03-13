package com.yem.hlm.backend.payments.repo;

import com.yem.hlm.backend.payments.domain.SchedulePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SchedulePaymentRepository extends JpaRepository<SchedulePayment, UUID> {

    /** All payments for a schedule item — ordered chronologically. */
    List<SchedulePayment> findByTenant_IdAndScheduleItemIdOrderByPaidAtAsc(
            UUID tenantId, UUID scheduleItemId);

    /** Sum of all payments made against a schedule item (for remaining-amount computation). */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p " +
           "WHERE p.tenant.id = :tenantId AND p.scheduleItemId = :itemId")
    BigDecimal sumPaidForItem(@Param("tenantId") UUID tenantId,
                              @Param("itemId") UUID itemId);

    /** Total collected in a calendar period (for cash KPI). */
    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p
        WHERE p.tenant.id = :tenantId
          AND p.paidAt BETWEEN :from AND :to
        """)
    BigDecimal sumCollectedInPeriod(@Param("tenantId") UUID tenantId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** Sum already paid against a list of schedule item IDs (used for overdue remaining calc). */
    @Query("""
        SELECT p.scheduleItemId, SUM(p.amountPaid) FROM SchedulePayment p
        WHERE p.tenant.id = :tenantId
          AND p.scheduleItemId IN :itemIds
        GROUP BY p.scheduleItemId
        """)
    List<Object[]> sumPaidByItemIds(@Param("tenantId") UUID tenantId,
                                    @Param("itemIds") List<UUID> itemIds);

    // ── Receivables dashboard aggregate queries ────────────────────────────────

    /** Total received all-time for the tenant (numerator of collection rate). */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p WHERE p.tenant.id = :tenantId")
    BigDecimal totalReceived(@Param("tenantId") UUID tenantId);

    /**
     * Pairs of {@code [issued_date, paid_date]} for computing average days-to-payment.
     * Returns {@code [DATE(psi.issued_at), DATE(sp.paid_at)]}.
     */
    @Query(value = """
        SELECT DATE(psi.issued_at), DATE(sp.paid_at)
        FROM schedule_payment sp
        JOIN payment_schedule_item psi ON psi.id = sp.schedule_item_id
        WHERE sp.tenant_id = :tenantId
          AND psi.issued_at IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> issuedAndReceivedPairs(@Param("tenantId") UUID tenantId);

    /**
     * Recent payments for the tenant as
     * {@code [id, amount_paid, DATE(paid_at), channel, project_name, property_ref, agent_email]}.
     */
    @Query(value = """
        SELECT sp.id, sp.amount_paid, DATE(sp.paid_at), sp.channel,
               p.name, prop.reference_code, u.email
        FROM schedule_payment sp
        JOIN payment_schedule_item psi ON psi.id = sp.schedule_item_id
        JOIN sale_contract sc ON sc.id = psi.contract_id
        JOIN project p ON p.id = sc.project_id
        JOIN property prop ON prop.id = sc.property_id
        JOIN app_user u ON u.id = sc.agent_id
        WHERE sp.tenant_id = :tenantId
        ORDER BY sp.paid_at DESC
        """, nativeQuery = true)
    List<Object[]> recentPayments(@Param("tenantId") UUID tenantId,
                                  org.springframework.data.domain.Pageable pageable);

    /** Agent-scoped variant of {@link #recentPayments(UUID, org.springframework.data.domain.Pageable)}. */
    @Query(value = """
        SELECT sp.id, sp.amount_paid, DATE(sp.paid_at), sp.channel,
               p.name, prop.reference_code, u.email
        FROM schedule_payment sp
        JOIN payment_schedule_item psi ON psi.id = sp.schedule_item_id
        JOIN sale_contract sc ON sc.id = psi.contract_id
        JOIN project p ON p.id = sc.project_id
        JOIN property prop ON prop.id = sc.property_id
        JOIN app_user u ON u.id = sc.agent_id
        WHERE sp.tenant_id = :tenantId
          AND sc.agent_id = :agentId
        ORDER BY sp.paid_at DESC
        """, nativeQuery = true)
    List<Object[]> recentPaymentsByAgent(@Param("tenantId") UUID tenantId,
                                         @Param("agentId") UUID agentId,
                                         org.springframework.data.domain.Pageable pageable);
}
