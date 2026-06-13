package com.yem.hlm.backend.societe.api.dto;

import java.time.LocalDate;

/**
 * Self-service CNDP compliance update (B-005).
 * Both fields are optional; null values leave the existing data unchanged.
 */
public record UpdateComplianceRequest(
        String    numeroCndp,
        LocalDate dateDeclarationCndp
) {}
