package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserActivatedEvent extends DomainEvent {
    public final UUID userId;
    public final String cguVersion;

    public UserActivatedEvent(UUID userId, UUID societeId, String cguVersion) {
        super(societeId, userId); // actor = the user activating their own account (unauthenticated flow)
        this.userId = userId;
        this.cguVersion = cguVersion;
    }
}
