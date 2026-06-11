package com.yem.hlm.backend.contact.domain;

/**
 * Buyer type under the Moroccan Office des Changes regulation — drives financing and
 * fund-transfer rules.
 */
public enum TypeAcquereur {
    /** Résident marocain (CIN). */
    RESIDENT_MAROC,
    /** Marocain Résidant à l'Étranger (CIN + titre de séjour). */
    MRE,
    /** Étranger non-résident (passeport). */
    ETRANGER
}
