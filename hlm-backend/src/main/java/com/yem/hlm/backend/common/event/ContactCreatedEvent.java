package com.yem.hlm.backend.common.event;

import java.util.UUID;

public class ContactCreatedEvent extends DomainEvent {
    private final UUID contactId;
    private final String fullName;

    public ContactCreatedEvent(UUID societeId, UUID actorUserId, UUID contactId, String fullName) {
        super(societeId, actorUserId);
        this.contactId = contactId;
        this.fullName = fullName;
    }

    public UUID getContactId() { return contactId; }
    public String getFullName() { return fullName; }
}
