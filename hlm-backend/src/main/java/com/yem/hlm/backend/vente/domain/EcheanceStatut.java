package com.yem.hlm.backend.vente.domain;

/**
 * Lifecycle statut of a payment milestone (échéance) in a Vente.
 */
public enum EcheanceStatut {
    /** Échéance à venir ou non encore réglée. */
    EN_ATTENTE,
    /** Paiement reçu et confirmé. */
    PAYEE,
    /** Date d'échéance dépassée sans paiement. */
    EN_RETARD
}
