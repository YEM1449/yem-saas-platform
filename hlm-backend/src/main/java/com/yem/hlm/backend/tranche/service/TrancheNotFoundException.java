package com.yem.hlm.backend.tranche.service;

import java.util.UUID;

public class TrancheNotFoundException extends RuntimeException {
    public TrancheNotFoundException(UUID id) {
        super("Tranche introuvable : " + id);
    }
}
