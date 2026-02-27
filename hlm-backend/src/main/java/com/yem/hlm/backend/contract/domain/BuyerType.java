package com.yem.hlm.backend.contract.domain;

/**
 * Buyer entity type — stored as part of the immutable buyer snapshot on {@link SaleContract}.
 * Captured at contract signing time; independent of future Contact record changes.
 *
 * <p>Currently defaults to {@code PERSON} because CRM contacts are all individuals
 * (ContactType values: PROSPECT, TEMP_CLIENT, CLIENT).
 * Extend when company (personne morale) contacts are introduced.
 */
public enum BuyerType {
    /** Individual buyer (personne physique). */
    PERSON,
    /** Company buyer (personne morale). */
    COMPANY
}
