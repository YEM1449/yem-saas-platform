package com.yem.hlm.backend.vente.domain;

/** Reason a sale was cancelled — required when advancing to {@link VenteStatut#ANNULE}. */
public enum MotifAnnulation {
    /** Mortgage application was rejected by the bank. */
    CREDIT_REFUSE,
    /** Buyer exercised the contractual cooling-off / reflection period. */
    DESISTEMENT_ACHETEUR,
    /** Condition suspensive (mortgage or other) not fulfilled within the deadline. */
    CSP_NON_REALISEE,
    /** Both parties mutually agreed to cancel. */
    ACCORD_PARTIES,
    /** Legal dispute between parties. */
    LITIGE,
    /** Other reason not covered by the above. */
    AUTRE
}
