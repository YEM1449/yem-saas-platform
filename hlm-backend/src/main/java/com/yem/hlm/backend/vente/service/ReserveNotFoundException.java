package com.yem.hlm.backend.vente.service;

import java.util.UUID;

/** Thrown when a delivery reserve is not found in the current société. Mapped to HTTP 404. */
public class ReserveNotFoundException extends RuntimeException {
    public ReserveNotFoundException(UUID id) {
        super("Réserve de livraison introuvable : " + id);
    }
}
