package com.yem.hlm.backend.vente.service;

import java.util.UUID;

/** Thrown when a vente has no financing file yet. Mapped to HTTP 404. */
public class DossierFinancementNotFoundException extends RuntimeException {
    public DossierFinancementNotFoundException(UUID venteId) {
        super("Aucun dossier de financement pour la vente : " + venteId);
    }
}
