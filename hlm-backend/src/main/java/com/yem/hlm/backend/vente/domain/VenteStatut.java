package com.yem.hlm.backend.vente.domain;

/**
 * Lifecycle statut of a Vente (sale) in the VEFA commercial pipeline (Loi 44-00, Maroc).
 *
 * <pre>
 * PROSPECT → OPTION → RESERVE → EN_RETRACTATION → ACOMPTE → COMPROMIS
 *          → FINANCEMENT → ACTE → LIVRE_AVEC_RESERVES → RESERVES_LEVEES → LIVRE_DEFINITIF
 * ANNULE = terminal (reachable from any non-terminal state).
 * </pre>
 *
 * <p>Identifiers are ASCII by codebase convention (statut is stored as VARCHAR).
 * Wave 12 renamed {@code ACTE_NOTARIE → ACTE} and {@code LIVRE → LIVRE_DEFINITIF}
 * (migrated by changeset 076) and added the Loi 44-00 stages.
 */
public enum VenteStatut {
    /** Intérêt commercial initial, pas encore d'engagement. */
    PROSPECT,
    /** Blocage temporaire du bien (24-72h) avant réservation. */
    OPTION,
    /** Réservation signée + dépôt de garantie (≤ 5%, Art. 618-4). */
    RESERVE,
    /** Délai légal de rétractation en cours (7j Maroc, Art. 618-3). */
    EN_RETRACTATION,
    /** Acompte versé. */
    ACOMPTE,
    /** Compromis de vente signé. */
    COMPROMIS,
    /** Financement en cours — acquéreur sécurise son crédit. */
    FINANCEMENT,
    /** Acte notarié signé — transfert légal de propriété (bien → SOLD). */
    ACTE,
    /** Bien livré mais réserves non levées. */
    LIVRE_AVEC_RESERVES,
    /** Toutes les réserves de livraison sont levées. */
    RESERVES_LEVEES,
    /** Livraison définitive, aucune réserve. */
    LIVRE_DEFINITIF,
    /** Vente annulée — terminal. */
    ANNULE
}
