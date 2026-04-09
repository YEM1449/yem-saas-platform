package com.yem.hlm.backend.tranche.api.dto;

import java.math.BigDecimal;

/**
 * Generation config for one floor within a building (or one "row" of villas).
 *
 * @param floorNumber   -1=sous-sol, 0=RDC, 1=1er, 2=2ème…  (use 0 for villas)
 * @param propertyType  APPARTEMENT | STUDIO | COMMERCE | DUPLEX | T2 | T3 | PARKING | VILLA
 * @param unitCount     number of identical units on this floor
 * @param surfaceMin    typical surface in m² (used for all generated units)
 * @param surfaceMax    upper bound in m² (informational)
 * @param prixBase      base price per unit (null = no price set)
 * @param orientation   cardinal orientation (SUD, NORD, SUD-EST…)
 * @param landAreaSqm   land area in m² — used for VILLA type
 * @param bedrooms      bedroom count — used for VILLA and T2/T3/DUPLEX
 * @param bathrooms     bathroom count — used for VILLA and T2/T3/DUPLEX
 * @param hasPool       whether units include a pool — used for VILLA
 * @param hasGarden     whether units include a garden — used for VILLA and ground-floor lots
 */
public record FloorConfig(
        int floorNumber,
        String propertyType,
        int unitCount,
        BigDecimal surfaceMin,
        BigDecimal surfaceMax,
        BigDecimal prixBase,
        String orientation,
        BigDecimal landAreaSqm,
        Integer bedrooms,
        Integer bathrooms,
        Boolean hasPool,
        Boolean hasGarden
) {}
