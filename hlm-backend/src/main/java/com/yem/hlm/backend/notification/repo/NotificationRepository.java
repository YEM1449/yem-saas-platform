package com.yem.hlm.backend.notification.repo;

import com.yem.hlm.backend.notification.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.societeId = :societeId
              and n.recipientUser.id = :recipientId
              and (:read is null or n.read = :read)
            order by n.createdAt desc
            """)
    List<Notification> findForRecipient(
            @Param("societeId") UUID societeId,
            @Param("recipientId") UUID recipientId,
            @Param("read") Boolean read,
            Pageable pageable
    );

    Optional<Notification> findBySocieteIdAndIdAndRecipientUser_Id(UUID societeId, UUID id, UUID recipientId);

    /** Returns notifications whose refId is in the provided set (for contact timeline). */
    @Query("""
            select n from Notification n
            where n.societeId = :societeId
              and n.refId IN :refIds
            order by n.createdAt desc
            """)
    List<Notification> findByTenantAndRefIds(
            @Param("societeId") UUID societeId,
            @Param("refIds") Collection<UUID> refIds
    );
}
