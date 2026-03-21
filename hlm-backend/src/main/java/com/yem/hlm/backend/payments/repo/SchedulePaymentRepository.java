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
    List<SchedulePayment> findBySocieteIdAndScheduleItemIdOrderByPaidAtAsc(
            UUID societeId, UUID scheduleItemId);

    /** Sum of all payments made against a schedule item (for remaining-amount computation). */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p " +
           "WHERE p.societeId = :societeId AND p.scheduleItemId = :itemId")
    BigDecimal sumPaidForItem(@Param("societeId") UUID societeId,
                              @Param("itemId") UUID itemId);

    /** Total collected in a calendar period (for cash KPI). */
    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p
        WHERE p.societeId = :societeId
          AND p.paidAt BETWEEN :from AND :to
        """)
    BigDecimal sumCollectedInPeriod(@Param("societeId") UUID societeId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** Sum already paid against a list of schedule item IDs (used for overdue remaining calc). */
    @Query("""
        SELECT p.scheduleItemId, SUM(p.amountPaid) FROM SchedulePayment p
        WHERE p.societeId = :societeId
          AND p.scheduleItemId IN :itemIds
        GROUP BY p.scheduleItemId
        """)
    List<Object[]> sumPaidByItemIds(@Param("societeId") UUID societeId,
                                    @Param("itemIds") List<UUID> itemIds);

    // ── Receivables dashboard aggregate queries ────────────────────────────────

    /** Total received all-time for the société (numerator of collection rate). */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM SchedulePayment p WHERE p.societeId = :societeId")
    BigDecimal totalReceived(@Param("societeId") UUID societeId);

    /**
     * Pairs of {@code [issued_date, paid_date]} for computing average days-to-payment.
     */
    @Query(value = """
        SELECT DATE(psi.issued_at), DATE(sp.paid_at)
        FROM schedule_payment sp
        JOIN payment_schedule_item psi ON psi.id = sp.schedule_item_id
        WHERE sp.societe_id = :societeId
          AND psi.issued_at IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> issuedAndReceivedPairs(@Param("societeId") UUID societeId);

    /**
     * Recent payments for the société.
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
        WHERE sp.societe_id = :societeId
        ORDER BY sp.paid_at DESC
        """, nativeQuery = true)
    List<Object[]> recentPayments(@Param("societeId") UUID societeId,
                                  org.springframework.data.domain.Pageable pageable);

    @Query(value = """
        SELECT sp.id, sp.amount_paid, DATE(sp.paid_at), sp.channel,
               p.name, prop.reference_code, u.email
        FROM schedule_payment sp
        JOIN payment_schedule_item psi ON psi.id = sp.schedule_item_id
        JOIN sale_contract sc ON sc.id = psi.contract_id
        JOIN project p ON p.id = sc.project_id
        JOIN property prop ON prop.id = sc.property_id
        JOIN app_user u ON u.id = sc.agent_id
        WHERE sp.societe_id = :societeId
          AND sc.agent_id = :agentId
        ORDER BY sp.paid_at DESC
        """, nativeQuery = true)
    List<Object[]> recentPaymentsByAgent(@Param("societeId") UUID societeId,
                                         @Param("agentId") UUID agentId,
                                         org.springframework.data.domain.Pageable pageable);
}
