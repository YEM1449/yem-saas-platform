package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class MembreAjouteEvent extends DomainEvent {
    public final UUID userId;
    public final String role;

    public MembreAjouteEvent(UUID societeId, UUID actorId, UUID userId, String role) {
        super(societeId, actorId);
        this.userId = userId;
        this.role = role;
    }
}
