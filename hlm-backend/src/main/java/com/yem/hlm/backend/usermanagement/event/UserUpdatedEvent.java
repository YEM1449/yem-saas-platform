package com.yem.hlm.backend.usermanagement.event;

import com.yem.hlm.backend.common.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public class UserUpdatedEvent extends DomainEvent {
    public final UUID userId;
    public final List<String> changedFields;

    public UserUpdatedEvent(UUID userId, UUID societeId, UUID actorId, List<String> changedFields) {
        super(societeId, actorId);
        this.userId = userId;
        this.changedFields = changedFields;
    }
}
