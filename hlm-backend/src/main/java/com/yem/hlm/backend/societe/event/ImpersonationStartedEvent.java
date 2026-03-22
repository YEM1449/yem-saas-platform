package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

/** Published when a SUPER_ADMIN starts an impersonation session (R7). */
public class ImpersonationStartedEvent extends DomainEvent {
    public final UUID targetUserId;
    public final String targetRole;

    public ImpersonationStartedEvent(UUID societeId, UUID superAdminId,
                                     UUID targetUserId, String targetRole) {
        super(societeId, superAdminId);
        this.targetUserId = targetUserId;
        this.targetRole = targetRole;
    }
}
