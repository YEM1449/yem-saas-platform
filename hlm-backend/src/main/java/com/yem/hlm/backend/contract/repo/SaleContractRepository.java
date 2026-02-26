package com.yem.hlm.backend.contract.repo;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleContractRepository extends JpaRepository<SaleContract, UUID> {

    /** Tenant-scoped lookup — returns 404 if tenant boundary is crossed. */
    Optional<SaleContract> findByTenant_IdAndId(UUID tenantId, UUID id);

    /**
     * Checks whether an active SIGNED contract already exists for a property.
     * Used as a service-layer guard before attempting to sign; the DB partial unique
     * index {@code uk_sc_property_signed} is the race-condition safety net.
     */
    boolean existsByTenant_IdAndProperty_IdAndStatusAndCanceledAtIsNull(
            UUID tenantId, UUID propertyId, SaleContractStatus status);

    /**
     * Filtered list for GET /api/contracts.
     * All parameters are optional (null = no filter).
     * Results are ordered by createdAt DESC.
     */
    @Query("""
            SELECT c FROM SaleContract c
            WHERE c.tenant.id = :tenantId
              AND (:status   IS NULL OR c.status       = :status)
              AND (:projectId IS NULL OR c.project.id  = :projectId)
              AND (:agentId   IS NULL OR c.agent.id    = :agentId)
              AND (:from      IS NULL OR c.signedAt    >= :from)
              AND (:to        IS NULL OR c.signedAt    <= :to)
            ORDER BY c.createdAt DESC
            """)
    List<SaleContract> filter(
            @Param("tenantId")  UUID tenantId,
            @Param("status")    SaleContractStatus status,
            @Param("projectId") UUID projectId,
            @Param("agentId")   UUID agentId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to
    );
}
