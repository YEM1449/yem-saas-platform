package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class SocieteDesactiveeEvent extends DomainEvent {
    public final String raison;

    public SocieteDesactiveeEvent(UUID societeId, UUID actorId, String raison) {
        super(societeId, actorId);
        this.raison = raison;
    }
}
