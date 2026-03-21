package com.yem.hlm.backend.audit.repo;

import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommercialAuditRepository extends JpaRepository<CommercialAuditEvent, UUID> {

    @Query("""
            SELECT e FROM CommercialAuditEvent e
            WHERE e.societeId = :societeId
              AND (CAST(:from AS LocalDateTime) IS NULL OR e.occurredAt >= :from)
              AND (CAST(:to   AS LocalDateTime) IS NULL OR e.occurredAt <= :to)
              AND (cast(:correlationType as string) IS NULL OR e.correlationType = :correlationType)
              AND (:correlationId IS NULL OR e.correlationId = :correlationId)
            ORDER BY e.occurredAt DESC
            """)
    List<CommercialAuditEvent> search(
            @Param("societeId") UUID societeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("correlationType") String correlationType,
            @Param("correlationId") UUID correlationId,
            Pageable pageable
    );

    /** Returns all audit events whose correlationId is in the provided set (for timeline). */
    @Query("""
            SELECT e FROM CommercialAuditEvent e
            WHERE e.societeId = :societeId
              AND e.correlationId IN :ids
            ORDER BY e.occurredAt DESC
            """)
    List<CommercialAuditEvent> findByTenantAndCorrelationIds(
            @Param("societeId") UUID societeId,
            @Param("ids") Collection<UUID> ids
    );
}
