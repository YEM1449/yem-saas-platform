package com.yem.hlm.backend.notification.service;

import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.notification.api.dto.NotificationResponse;
import com.yem.hlm.backend.notification.domain.Notification;
import com.yem.hlm.backend.notification.domain.NotificationType;
import com.yem.hlm.backend.notification.repo.NotificationRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
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
    public void notify(Tenant tenant, User recipient, NotificationType type, UUID refId, String payload) {
        try {
            notificationRepository.save(new Notification(tenant, recipient, type, refId, payload));
        } catch (DataIntegrityViolationException ignored) {
            // Dedupe index hit -> ignore
        }
    }

    public List<NotificationResponse> list(Boolean read, int size) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        var page = PageRequest.of(0, Math.max(1, Math.min(size, 200)));
        return notificationRepository.findForRecipient(tenantId, userId, read, page)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        Notification n = notificationRepository.findByTenant_IdAndIdAndRecipientUser_Id(tenantId, notificationId, userId)
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

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new CrossTenantAccessException("Missing tenant context");
        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId = TenantContext.getUserId();
        if (userId == null) throw new CrossTenantAccessException("Missing user context");
        return userId;
    }
}
