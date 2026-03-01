package com.yem.hlm.backend.payment.repo;

import com.yem.hlm.backend.payment.domain.PaymentTranche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTrancheRepository extends JpaRepository<PaymentTranche, UUID> {

    /** Tenant-scoped lookup. */
    Optional<PaymentTranche> findByTenant_IdAndId(UUID tenantId, UUID id);

    /**
     * Overdue tranche detection: ISSUED or PARTIALLY_PAID tranches where due_date < today.
     * Used by the overdue scheduler.
     */
    @Query("""
            SELECT t FROM PaymentTranche t
            WHERE t.tenant.id = :tenantId
              AND t.status IN (
                com.yem.hlm.backend.payment.domain.TrancheStatus.ISSUED,
                com.yem.hlm.backend.payment.domain.TrancheStatus.PARTIALLY_PAID
              )
              AND t.dueDate IS NOT NULL AND t.dueDate < :today
            """)
    List<PaymentTranche> findOverdueTranches(@Param("tenantId") UUID tenantId,
                                             @Param("today") LocalDate today);

    /** Distinct tenant IDs that have active (non-PLANNED, non-PAID) tranches. */
    @Query("SELECT DISTINCT t.tenant.id FROM PaymentTranche t WHERE t.status IN " +
           "(com.yem.hlm.backend.payment.domain.TrancheStatus.ISSUED, " +
           "com.yem.hlm.backend.payment.domain.TrancheStatus.PARTIALLY_PAID)")
    List<UUID> findActiveTenantIds();
}
