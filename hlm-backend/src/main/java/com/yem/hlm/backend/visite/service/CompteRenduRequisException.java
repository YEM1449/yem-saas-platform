package com.yem.hlm.backend.visite.service;

/** Thrown when a visite is marked REALISEE without a compte-rendu + résultat (RG-V06, 422). */
public class CompteRenduRequisException extends RuntimeException {
    public CompteRenduRequisException() {
        super("Un compte-rendu et un résultat sont requis pour marquer la visite réalisée.");
    }
}
