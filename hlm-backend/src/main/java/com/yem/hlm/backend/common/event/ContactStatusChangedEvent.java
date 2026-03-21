package com.yem.hlm.backend.common.event;

import com.yem.hlm.backend.contact.domain.ContactStatus;
import java.util.UUID;

public class ContactStatusChangedEvent extends DomainEvent {
    private final UUID contactId;
    private final ContactStatus from;
    private final ContactStatus to;

    public ContactStatusChangedEvent(UUID societeId, UUID actorUserId, UUID contactId,
                                      ContactStatus from, ContactStatus to) {
        super(societeId, actorUserId);
        this.contactId = contactId;
        this.from = from;
        this.to = to;
    }

    public UUID getContactId() { return contactId; }
    public ContactStatus getFrom() { return from; }
    public ContactStatus getTo() { return to; }
}
