package com.yem.hlm.backend.viewer3d.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight snapshot used by the 3D viewer for colour-coding.
 * Polled every 30 s; must stay small — no full property payload.
 *
 * <p>{@code statut} is one of: DISPONIBLE, RESERVE, VENDU, LIVRE
 * (mapped from the raw PropertyStatus enum in the service layer).
 */
public record Lot3dStatusDto(
        String    meshId,
        UUID      lotId,
        String    statut,
        String    typology,
        BigDecimal surface,
        BigDecimal prix
) {}
