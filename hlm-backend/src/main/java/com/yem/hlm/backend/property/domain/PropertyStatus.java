package com.yem.hlm.backend.property.domain;

/**
 * Defines the lifecycle status of a property in the CRM-HLM system.
 * <p>
 * Status transitions typically follow this flow:
 * DRAFT → ACTIVE → RESERVED → SOLD
 * <p>
 * Alternative endings: WITHDRAWN (removed from market), ARCHIVED (historical record)
 */
public enum PropertyStatus {
    /**
     * Property is being prepared and not yet visible to prospects.
     */
    DRAFT,

    /**
     * Property is live and available on the market.
     */
    ACTIVE,

    /**
     * Property has an active deposit/reservation.
     */
    RESERVED,

    /**
     * Transaction completed, property is sold.
     */
    SOLD,

    /**
     * Property was removed from the market without being sold.
     */
    WITHDRAWN,

    /**
     * Historical record, no longer actively managed.
     */
    ARCHIVED
}
