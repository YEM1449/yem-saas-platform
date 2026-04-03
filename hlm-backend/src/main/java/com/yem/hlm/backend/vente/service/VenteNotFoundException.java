package com.yem.hlm.backend.vente.service;

import java.util.UUID;

public class VenteNotFoundException extends RuntimeException {
    public VenteNotFoundException(UUID id) {
        super("Vente not found: " + id);
    }
}
