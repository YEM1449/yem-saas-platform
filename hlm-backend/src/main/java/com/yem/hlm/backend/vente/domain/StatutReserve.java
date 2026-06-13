package com.yem.hlm.backend.vente.domain;

/** Lifecycle of a delivery reserve (réserve de livraison). */
public enum StatutReserve {
    /** Constatée, pas encore traitée. */
    EN_ATTENTE,
    /** Traitement en cours. */
    EN_COURS,
    /** Réserve levée. */
    LEVEE
}
