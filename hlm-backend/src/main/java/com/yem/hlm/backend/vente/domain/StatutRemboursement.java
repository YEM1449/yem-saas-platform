package com.yem.hlm.backend.vente.domain;

/** Statut du remboursement du dépôt après annulation/rétractation (#028). */
public enum StatutRemboursement {
    /** Dû à l'acquéreur, pas encore versé. */
    DU,
    /** Versé à l'acquéreur. */
    EFFECTUE,
    /** Annulé (ex. dépôt finalement conservé conformément au contrat). */
    ANNULE
}
