package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserRoleChangedEvent extends DomainEvent {
    public final UUID userId;
    public final String ancienRole;
    public final String nouveauRole;
    public final String raison;

    public UserRoleChangedEvent(UUID userId, UUID societeId, UUID actorId,
                                 String ancienRole, String nouveauRole, String raison) {
        super(societeId, actorId);
        this.userId = userId;
        this.ancienRole = ancienRole;
        this.nouveauRole = nouveauRole;
        this.raison = raison;
    }
}
