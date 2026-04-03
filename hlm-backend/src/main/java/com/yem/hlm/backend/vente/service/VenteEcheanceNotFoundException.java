package com.yem.hlm.backend.vente.service;

import java.util.UUID;

public class VenteEcheanceNotFoundException extends RuntimeException {
    public VenteEcheanceNotFoundException(UUID id) {
        super("Vente échéance not found: " + id);
    }
}
