package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.Societe;

import java.time.LocalDate;
import java.util.UUID;

/** CNDP compliance data for the caller's société (B-005). */
public record ComplianceInfoResponse(
        UUID      societeId,
        String    societeNom,
        String    numeroCndp,
        LocalDate dateDeclarationCndp,
        /** True when numeroCndp is recorded — indicates CNDP declaration is on file. */
        boolean   cndpDeclare
) {
    public static ComplianceInfoResponse from(Societe s) {
        return new ComplianceInfoResponse(
                s.getId(), s.getNom(),
                s.getNumeroCndp(), s.getDateDeclarationCndp(),
                s.getNumeroCndp() != null && !s.getNumeroCndp().isBlank());
    }
}
