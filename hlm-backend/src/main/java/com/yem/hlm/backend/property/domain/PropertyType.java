package com.yem.hlm.backend.property.domain;

/**
 * Defines the property types available in the CRM-HLM real estate system.
 * <p>
 * Each type has specific validation rules for physical characteristics:
 * <ul>
 *   <li><b>VILLA</b>: Detached house with land. Requires surface_area, land_area, bedrooms, bathrooms</li>
 *   <li><b>DUPLEX</b>: Two-story residential unit. Requires surface_area, bedrooms, bathrooms, floors</li>
 *   <li><b>APPARTEMENT</b>: Apartment in a building. Requires surface_area, bedrooms, bathrooms, floor_number</li>
 *   <li><b>LOT</b>: Developed land plot (serviced). Requires land_area, zoning, is_serviced</li>
 *   <li><b>TERRAIN_VIERGE</b>: Undeveloped land. Requires only land_area</li>
 * </ul>
 * <p>
 * Type-specific validation is enforced at the service layer.
 */
public enum PropertyType {
    /**
     * Detached house with land (villa).
     */
    VILLA,

    /**
     * Two-story residential unit (duplex).
     */
    DUPLEX,

    /**
     * Apartment in a building (appartement).
     */
    APPARTEMENT,

    /**
     * Developed land plot with utilities (lot viabilisé).
     */
    LOT,

    /**
     * Undeveloped land (terrain vierge).
     */
    TERRAIN_VIERGE
}
