package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class UserInvitedEvent extends DomainEvent {
    public final UUID userId;
    public final String role;
    public final String messagePersonnalise;

    public UserInvitedEvent(UUID userId, UUID societeId, UUID actorId, String role, String messagePersonnalise) {
        super(societeId, actorId);
        this.userId = userId;
        this.role = role;
        this.messagePersonnalise = messagePersonnalise;
    }
}
