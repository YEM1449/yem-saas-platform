package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class SocieteCreatedEvent extends DomainEvent {
    public final String nom;

    public SocieteCreatedEvent(UUID societeId, UUID actorId, String nom) {
        super(societeId, actorId);
        this.nom = nom;
    }
}
