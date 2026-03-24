package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class SocieteReactiveeEvent extends DomainEvent {
    public SocieteReactiveeEvent(UUID societeId, UUID actorId) {
        super(societeId, actorId);
    }
}
