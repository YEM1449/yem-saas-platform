package com.yem.hlm.backend.notification.service;

import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.notification.api.dto.NotificationResponse;
import com.yem.hlm.backend.notification.domain.Notification;
import com.yem.hlm.backend.notification.domain.NotificationType;
import com.yem.hlm.backend.notification.repo.NotificationRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.user.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Create a notification. If a dedupe unique index blocks insertion, we ignore it.
     */
    @Transactional
    public void notify(UUID societeId, User recipient, NotificationType type, UUID refId, String payload) {
        try {
            notificationRepository.save(new Notification(societeId, recipient, type, refId, payload));
        } catch (DataIntegrityViolationException ignored) {
            // Dedupe index hit -> ignore
        }
    }

    public List<NotificationResponse> list(Boolean read, int size) {
        UUID societeId = requireSocieteId();
        UUID userId = requireUserId();

        var page = PageRequest.of(0, Math.max(1, Math.min(size, 200)));
        return notificationRepository.findForRecipient(societeId, userId, read, page)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID societeId = requireSocieteId();
        UUID userId = requireUserId();

        Notification n = notificationRepository.findBySocieteIdAndIdAndRecipientUser_Id(societeId, notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        n.markRead();
        Notification saved = notificationRepository.save(n);
        return toResponse(saved);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getRefId(),
                n.getPayload(),
                n.isRead(),
                n.getCreatedAt()
        );
    }

    private UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) throw new CrossTenantAccessException("Missing société context");
        return societeId;
    }

    private UUID requireUserId() {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) throw new CrossTenantAccessException("Missing user context");
        return userId;
    }
}
