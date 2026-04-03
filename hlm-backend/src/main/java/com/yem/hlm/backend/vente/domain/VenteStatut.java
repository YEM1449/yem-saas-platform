package com.yem.hlm.backend.vente.domain;

/**
 * Lifecycle statut of a Vente (sale) in the commercial pipeline.
 *
 * <pre>
 * COMPROMIS ──► FINANCEMENT ──► ACTE_NOTARIE ──► LIVRE
 *     │               │               │
 *     └───────────────┴───────────────┴──► ANNULE
 * </pre>
 */
public enum VenteStatut {
    /** Compromis de vente signé — initial purchase agreement. */
    COMPROMIS,
    /** Financement en cours — buyer securing mortgage or financing. */
    FINANCEMENT,
    /** Acte notarié signé — final notarial deed executed. */
    ACTE_NOTARIE,
    /** Bien livré à l'acquéreur — property handed over to buyer. */
    LIVRE,
    /** Vente annulée — sale cancelled at any stage. */
    ANNULE
}
