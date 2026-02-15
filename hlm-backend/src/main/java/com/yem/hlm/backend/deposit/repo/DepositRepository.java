package com.yem.hlm.backend.deposit.repo;

import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    Optional<Deposit> findByTenant_IdAndId(UUID tenantId, UUID id);

    boolean existsByTenant_IdAndContact_IdAndPropertyId(UUID tenantId, UUID contactId, UUID propertyId);

    boolean existsByTenant_IdAndPropertyIdAndStatusIn(UUID tenantId, UUID propertyId, List<DepositStatus> statuses);

    List<Deposit> findAllByTenant_IdAndContact_IdAndStatus(UUID tenantId, UUID contactId, DepositStatus status);

    List<Deposit> findAllByTenant_IdAndStatusAndDueDateBefore(UUID tenantId, DepositStatus status, LocalDateTime before);

    // For scheduler workflows (cross-tenant, runs without TenantContext)
    List<Deposit> findAllByStatusAndDueDateBefore(DepositStatus status, LocalDateTime before);

    List<Deposit> findAllByStatusAndDueDateBetween(DepositStatus status, LocalDateTime from, LocalDateTime to);


    @Query("""
            select d from Deposit d
            where d.tenant.id = :tenantId
              and (:status is null or d.status = :status)
              and (:agentId is null or d.agent.id = :agentId)
              and (:contactId is null or d.contact.id = :contactId)
              and (:propertyId is null or d.propertyId = :propertyId)
              and (cast(:from as LocalDateTime) is null or d.createdAt >= :from)
              and (cast(:to as LocalDateTime) is null or d.createdAt <= :to)
            order by d.createdAt desc
            """)
    List<Deposit> report(
            @Param("tenantId") UUID tenantId,
            @Param("status") DepositStatus status,
            @Param("agentId") UUID agentId,
            @Param("contactId") UUID contactId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select d from Deposit d
            where d.tenant.id = :tenantId
              and d.status = 'PENDING'
              and d.dueDate is not null
              and d.dueDate >= :from
              and d.dueDate <= :to
            """)
    List<Deposit> findDueSoon(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
