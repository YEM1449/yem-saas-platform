package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserUnblockedEvent extends DomainEvent {
    public final UUID userId;

    public UserUnblockedEvent(UUID userId, UUID societeId, UUID actorId) {
        super(societeId, actorId);
        this.userId = userId;
    }
}
