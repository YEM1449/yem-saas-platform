package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserAnonymizedEvent extends DomainEvent {
    public final UUID userId;

    public UserAnonymizedEvent(UUID userId, UUID societeId, UUID actorId) {
        super(societeId, actorId);
        this.userId = userId;
    }
}
