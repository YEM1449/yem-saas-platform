package com.yem.hlm.backend.vente.service;

import java.util.UUID;

/** Thrown when a co-buyer is not found in the current société. Mapped to HTTP 404. */
public class CoAcquereurNotFoundException extends RuntimeException {
    public CoAcquereurNotFoundException(UUID id) {
        super("Co-acquéreur introuvable : " + id);
    }
}
