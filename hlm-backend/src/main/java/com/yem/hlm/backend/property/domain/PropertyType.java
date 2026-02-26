package com.yem.hlm.backend.property.domain;

/**
 * Defines the property types available in the CRM-HLM real estate system.
 * <p>
 * Each type has specific validation rules for physical characteristics.
 * Types are grouped into logical categories via {@link PropertyCategory}:
 * <ul>
 *   <li><b>VILLA</b> → VILLA. Requires surface_area, land_area, bedrooms, bathrooms</li>
 *   <li><b>APPARTEMENT</b> → APARTMENT. Requires surface_area, bedrooms, bathrooms, floor_number</li>
 *   <li><b>DUPLEX</b> → APARTMENT. Requires surface_area, bedrooms, bathrooms, floors</li>
 *   <li><b>STUDIO</b> → APARTMENT. Requires surface_area, floor_number</li>
 *   <li><b>T2</b> → APARTMENT. Requires surface_area, bedrooms, bathrooms, floor_number</li>
 *   <li><b>T3</b> → APARTMENT. Requires surface_area, bedrooms, bathrooms, floor_number</li>
 *   <li><b>COMMERCE</b> → COMMERCE. Requires surface_area</li>
 *   <li><b>LOT</b> → LAND. Requires land_area, zoning, is_serviced</li>
 *   <li><b>TERRAIN_VIERGE</b> → LAND. Requires only land_area</li>
 * </ul>
 * <p>
 * Type-specific validation is enforced at the service layer.
 */
public enum PropertyType {

    /** Detached house with land (villa). */
    VILLA,

    /** Apartment in a building (generic). */
    APPARTEMENT,

    /** Two-story residential unit (duplex). */
    DUPLEX,

    /** Studio apartment (single room, no separate bedroom). */
    STUDIO,

    /** Two-room apartment. */
    T2,

    /** Three-room apartment. */
    T3,

    /** Commercial space (retail, office, etc.). */
    COMMERCE,

    /** Developed land plot with utilities (lot viabilisé). */
    LOT,

    /** Undeveloped land (terrain vierge). */
    TERRAIN_VIERGE;

    /**
     * Returns the logical category this type belongs to.
     */
    public PropertyCategory category() {
        return switch (this) {
            case VILLA -> PropertyCategory.VILLA;
            case APPARTEMENT, DUPLEX, STUDIO, T2, T3 -> PropertyCategory.APARTMENT;
            case COMMERCE -> PropertyCategory.COMMERCE;
            case LOT, TERRAIN_VIERGE -> PropertyCategory.LAND;
        };
    }
}
