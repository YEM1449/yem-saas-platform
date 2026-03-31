package com.yem.hlm.backend.common.event;

import java.util.UUID;

/**
 * Published when a contact expresses interest in a property
 * (e.g., visit request, inquiry, reservation).
 * Used to auto-promote prospect status.
 */
public class PropertyInterestEvent extends DomainEvent {

    private final UUID contactId;
    private final UUID propertyId;
    private final String interestType; // "INTEREST", "RESERVATION", "DEPOSIT"

    public PropertyInterestEvent(UUID societeId, UUID actorUserId,
                                  UUID contactId, UUID propertyId, String interestType) {
        super(societeId, actorUserId);
        this.contactId = contactId;
        this.propertyId = propertyId;
        this.interestType = interestType;
    }

    public UUID getContactId() { return contactId; }
    public UUID getPropertyId() { return propertyId; }
    public String getInterestType() { return interestType; }
}
