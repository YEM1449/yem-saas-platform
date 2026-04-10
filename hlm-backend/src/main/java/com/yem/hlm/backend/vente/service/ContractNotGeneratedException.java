package com.yem.hlm.backend.vente.service;

import java.util.UUID;

/** Thrown when a sign action is attempted before the contract has been generated. */
public class ContractNotGeneratedException extends RuntimeException {

    public ContractNotGeneratedException(UUID venteId) {
        super("Le contrat de la vente " + venteId + " doit être généré avant signature. "
              + "Appelez POST /api/ventes/{id}/contract/generate d'abord.");
    }
}
