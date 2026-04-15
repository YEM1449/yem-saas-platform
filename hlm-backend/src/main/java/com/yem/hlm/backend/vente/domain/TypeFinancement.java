package com.yem.hlm.backend.vente.domain;

/** Financing type for a real estate sale. */
public enum TypeFinancement {
    /** Buyer pays the full price in cash — no mortgage involved. */
    COMPTANT,
    /** Standard mortgage (prêt immobilier) through a bank or lending institution. */
    CREDIT_IMMOBILIER,
    /** Prêt à Taux Zéro — government zero-interest loan for first-time buyers. */
    PTZ,
    /** Mix of personal contribution + mortgage (e.g. PTZ + bank credit). */
    MIXTE
}
