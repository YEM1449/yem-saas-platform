package com.yem.hlm.backend.outbox.repo;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.MessageStatus;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {

    Optional<OutboundMessage> findByTenant_IdAndId(UUID tenantId, UUID id);

    /**
     * Paged, tenant-scoped list for the messages API.
     * All filter params are optional (null disables the constraint).
     */
    @Query("""
            SELECT m FROM OutboundMessage m
            WHERE m.tenant.id = :tenantId
              AND (cast(:channel as string) IS NULL OR m.channel = :channel)
              AND (cast(:status as string) IS NULL OR m.status = :status)
              AND (:correlationId IS NULL OR m.correlationId = :correlationId)
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt <= :to)
            ORDER BY m.createdAt DESC
            """)
    Page<OutboundMessage> findByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("channel") MessageChannel channel,
            @Param("status") MessageStatus status,
            @Param("correlationId") UUID correlationId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    /**
     * Native query using FOR UPDATE SKIP LOCKED to claim a batch of PENDING
     * messages for dispatch without blocking other concurrent workers.
     *
     * <p>NOTE: this must be called inside an active {@code @Transactional} context.
     * The lock is released when the surrounding transaction commits.
     */
    @Query(value = """
            SELECT id FROM outbound_message
            WHERE status = 'PENDING'
              AND next_retry_at <= CURRENT_TIMESTAMP
            ORDER BY next_retry_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> fetchPendingBatch(@Param("batchSize") int batchSize);
}
