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
}
