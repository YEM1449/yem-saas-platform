package com.yem.hlm.backend.societe.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.UUID;

public class MembreRoleModifieEvent extends DomainEvent {
    public final UUID userId;
    public final String ancienRole;
    public final String nouveauRole;

    public MembreRoleModifieEvent(UUID societeId, UUID actorId,
                                  UUID userId, String ancienRole, String nouveauRole) {
        super(societeId, actorId);
        this.userId = userId;
        this.ancienRole = ancienRole;
        this.nouveauRole = nouveauRole;
    }
}
