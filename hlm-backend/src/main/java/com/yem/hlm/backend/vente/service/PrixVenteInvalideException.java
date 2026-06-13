package com.yem.hlm.backend.vente.service;

/** Thrown when prixVente is explicitly provided but is zero or negative (A-004). */
public class PrixVenteInvalideException extends RuntimeException {
    public PrixVenteInvalideException() {
        super("Le prix de vente fourni doit être strictement positif.");
    }
}
