package com.yem.hlm.backend.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class DomainEvent {
    private final UUID societeId;
    private final UUID actorUserId;
    private final LocalDateTime occurredAt = LocalDateTime.now();

    protected DomainEvent(UUID societeId, UUID actorUserId) {
        this.societeId = societeId;
        this.actorUserId = actorUserId;
    }

    public UUID getSocieteId() { return societeId; }
    public UUID getActorUserId() { return actorUserId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
