package com.yem.hlm.backend.payment.repo;

import com.yem.hlm.backend.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** All payments for a call (tenant-scoped via call FK). */
    List<Payment> findByTenant_IdAndPaymentCall_IdOrderByReceivedAtAsc(UUID tenantId, UUID callId);

    /** Sum of amount_received for a call — null when no payments exist. */
    @Query("SELECT COALESCE(SUM(p.amountReceived), 0) FROM Payment p " +
           "WHERE p.tenant.id = :tenantId AND p.paymentCall.id = :callId")
    BigDecimal sumReceivedByCall(@Param("tenantId") UUID tenantId,
                                 @Param("callId") UUID callId);

    // =========================================================================
    // Receivables dashboard aggregate queries (no entity hydration)
    // =========================================================================

    /** Total cash collected across all payments for a tenant. */
    @Query("SELECT COALESCE(SUM(p.amountReceived), 0) FROM Payment p WHERE p.tenant.id = :tenantId")
    BigDecimal totalReceived(@Param("tenantId") UUID tenantId);

    /**
     * (issuedAt, receivedAt) pairs for computing avgDaysToPayment.
     * Returns rows: [issuedAt(LocalDateTime), receivedAt(LocalDate)].
     */
    @Query("""
            SELECT pc.issuedAt, p.receivedAt
            FROM Payment p
            JOIN p.paymentCall pc
            WHERE p.tenant.id = :tenantId
              AND pc.issuedAt IS NOT NULL
            """)
    List<Object[]> issuedAndReceivedPairs(@Param("tenantId") UUID tenantId);

    /**
     * Last N payments received, ordered by receivedAt DESC then createdAt DESC.
     * Returns rows: [paymentId(UUID), amountReceived(BigDecimal), receivedAt(LocalDate),
     *                method(String), projectName(String), propertyRef(String), agentEmail(String)].
     */
    @Query("""
            SELECT p.id, p.amountReceived, p.receivedAt, p.method,
                   sc.project.name, sc.property.referenceCode, sc.agent.email
            FROM Payment p
            JOIN p.paymentCall pc
            JOIN pc.tranche pt
            JOIN pt.schedule ps
            JOIN ps.saleContract sc
            WHERE p.tenant.id = :tenantId
              AND (:agentId IS NULL OR sc.agent.id = :agentId)
            ORDER BY p.receivedAt DESC, p.createdAt DESC
            """)
    List<Object[]> recentPayments(@Param("tenantId") UUID tenantId,
                                  @Param("agentId") UUID agentId,
                                  Pageable pageable);
}
