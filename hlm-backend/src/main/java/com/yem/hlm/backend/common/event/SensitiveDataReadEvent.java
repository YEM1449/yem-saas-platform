package com.yem.hlm.backend.common.event;

import java.util.UUID;

/** Published by {@code ReadAuditAspect} whenever a {@code @ReadAudit}-annotated endpoint is called (B-004). */
public class SensitiveDataReadEvent extends DomainEvent {

    private final String entityType;
    private final UUID   entityId;

    public SensitiveDataReadEvent(UUID societeId, UUID actorUserId,
                                  String entityType, UUID entityId) {
        super(societeId, actorUserId);
        this.entityType = entityType;
        this.entityId   = entityId;
    }

    public String getEntityType() { return entityType; }
    public UUID   getEntityId()   { return entityId; }
}
