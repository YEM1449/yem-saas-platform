package com.yem.hlm.backend.reservation.service;

import java.util.UUID;

public class PropertyNotAvailableForReservationException extends RuntimeException {
    public PropertyNotAvailableForReservationException(UUID propertyId) {
        super("Property " + propertyId + " is not available for reservation (already RESERVED, SOLD or not ACTIVE)");
    }
}
