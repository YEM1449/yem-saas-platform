package com.yem.hlm.backend.tranche.api.dto;

import java.util.List;

/**
 * Generation config for one building (Immeuble) within a tranche.
 *
 * @param buildingOrder global sequential order across ALL tranches (A=0, B=1, C=2…)
 * @param customName    custom building name (used when naming = CUSTOM)
 * @param floorCount    number of floors ABOVE RDC (R+N)
 * @param hasRdc        whether the building has a rez-de-chaussée
 * @param rdcType       type for RDC units: COMMERCE | APPARTEMENT | NONE
 * @param rdcUnitCount  number of units at RDC
 * @param hasParking    whether parking spaces should be generated (floor -1)
 * @param parkingCount  number of parking bays to generate
 * @param floors        per-floor configuration (RDC + upper floors)
 */
public record BuildingConfig(
        int buildingOrder,
        String customName,
        int floorCount,
        boolean hasRdc,
        String rdcType,
        int rdcUnitCount,
        boolean hasParking,
        int parkingCount,
        List<FloorConfig> floors
) {}
