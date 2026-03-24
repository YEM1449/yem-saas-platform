package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class MembreRetireEvent extends DomainEvent {
    public final UUID userId;

    public MembreRetireEvent(UUID societeId, UUID actorId, UUID userId) {
        super(societeId, actorId);
        this.userId = userId;
    }
}
