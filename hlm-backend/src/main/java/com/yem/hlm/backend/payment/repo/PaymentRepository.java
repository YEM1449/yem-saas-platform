package com.yem.hlm.backend.payment.repo;

import com.yem.hlm.backend.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** All payments for a call (tenant-scoped via call FK). */
    List<Payment> findByTenant_IdAndPaymentCall_IdOrderByReceivedAtAsc(UUID tenantId, UUID callId);

    /** Sum of amount_received for a call — null when no payments exist. */
    @Query("SELECT COALESCE(SUM(p.amountReceived), 0) FROM Payment p " +
           "WHERE p.tenant.id = :tenantId AND p.paymentCall.id = :callId")
    BigDecimal sumReceivedByCall(@Param("tenantId") UUID tenantId,
                                 @Param("callId") UUID callId);
}
