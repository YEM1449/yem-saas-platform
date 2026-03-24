package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserRemovedEvent extends DomainEvent {
    public final UUID userId;
    public final String raison;

    public UserRemovedEvent(UUID userId, UUID societeId, UUID actorId, String raison) {
        super(societeId, actorId);
        this.userId = userId;
        this.raison = raison;
    }
}
