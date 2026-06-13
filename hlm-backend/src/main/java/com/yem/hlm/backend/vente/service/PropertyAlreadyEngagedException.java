package com.yem.hlm.backend.vente.service;

import java.util.UUID;

/**
 * Thrown when attempting to create a vente for a property that already has an
 * active (non-cancelled) vente. Enforces RG-B03: at most one active engagement
 * per property at a time. Mapped to HTTP 409 (PROPERTY_ALREADY_ENGAGED).
 */
public class PropertyAlreadyEngagedException extends RuntimeException {
    public PropertyAlreadyEngagedException(UUID propertyId) {
        super("Ce bien fait déjà l'objet d'une vente active. "
                + "Annulez la vente existante avant d'en créer une nouvelle (bien : " + propertyId + ").");
    }
}
