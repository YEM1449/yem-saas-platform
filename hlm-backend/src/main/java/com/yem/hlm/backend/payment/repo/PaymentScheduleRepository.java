package com.yem.hlm.backend.payment.repo;

import com.yem.hlm.backend.payment.domain.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {

    /** Tenant-scoped lookup by contract ID. */
    Optional<PaymentSchedule> findByTenant_IdAndSaleContract_Id(UUID tenantId, UUID contractId);

    /** True if a schedule already exists for this contract (for duplicate-guard). */
    boolean existsByTenant_IdAndSaleContract_Id(UUID tenantId, UUID contractId);

    /**
     * Loads a schedule with all tranches eagerly — used by service to validate totals
     * and build response objects without N+1.
     */
    @Query("""
            SELECT ps FROM PaymentSchedule ps
            JOIN FETCH ps.tenant
            JOIN FETCH ps.saleContract sc
            JOIN FETCH sc.tenant
            LEFT JOIN FETCH ps.tranches
            WHERE ps.tenant.id = :tenantId AND ps.saleContract.id = :contractId
            """)
    Optional<PaymentSchedule> findWithTranches(@Param("tenantId") UUID tenantId,
                                               @Param("contractId") UUID contractId);
}
