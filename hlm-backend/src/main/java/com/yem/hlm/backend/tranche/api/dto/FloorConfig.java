package com.yem.hlm.backend.tranche.api.dto;

import java.math.BigDecimal;

/**
 * Generation config for one floor within a building.
 *
 * @param floorNumber  -1=sous-sol, 0=RDC, 1=1er, 2=2ème…
 * @param propertyType APPARTEMENT | STUDIO | COMMERCE | DUPLEX | T2 | T3 | PARKING
 * @param unitCount    number of identical units on this floor
 * @param surfaceMin   typical surface (used for all generated units)
 * @param surfaceMax   upper bound (informational)
 * @param prixBase     base price per unit (null = no price set)
 * @param orientation  cardinal orientation (SUD, NORD, SUD-EST…)
 */
public record FloorConfig(
        int floorNumber,
        String propertyType,
        int unitCount,
        BigDecimal surfaceMin,
        BigDecimal surfaceMax,
        BigDecimal prixBase,
        String orientation
) {}
