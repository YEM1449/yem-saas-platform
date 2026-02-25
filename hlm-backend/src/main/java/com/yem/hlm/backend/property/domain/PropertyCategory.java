package com.yem.hlm.backend.property.domain;

/**
 * Logical grouping of property types for display and filtering.
 * <p>
 * Maps multiple {@link PropertyType} values to a broader category:
 * <ul>
 *   <li>VILLA → VILLA</li>
 *   <li>APPARTEMENT, DUPLEX, STUDIO, T2, T3 → APARTMENT</li>
 *   <li>LOT, TERRAIN_VIERGE → LAND</li>
 *   <li>COMMERCE → COMMERCE</li>
 * </ul>
 */
public enum PropertyCategory {
    VILLA,
    APARTMENT,
    LAND,
    COMMERCE
}
