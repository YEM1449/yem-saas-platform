package com.yem.hlm.backend.portal.api.dto;

import com.yem.hlm.backend.vente.domain.Vente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slim contract view for the buyer portal.
 *
 * <p>status values: "PENDING" | "GENERATED" | "SIGNED" (vente pipeline).
 * docId is the UUID of the CONTRAT_GENERE VenteDocument — non-null when a PDF exists.
 */
public record PortalContractResponse(
        UUID id,
        String propertyRef,
        String propertyType,
        String projectName,
        String status,
        BigDecimal agreedPrice,
        LocalDateTime signedAt,
        UUID docId
) {
    public static PortalContractResponse fromVente(Vente v,
                                                   String propertyRef,
                                                   String propertyType,
                                                   String projectName,
                                                   UUID docId) {
        return new PortalContractResponse(
                v.getId(),
                propertyRef,
                propertyType,
                projectName,
                v.getContractStatus().name(),
                v.getPrixVente(),
                v.getDateActeNotarie() != null
                        ? v.getDateActeNotarie().atStartOfDay()
                        : v.getDateCompromis() != null
                                ? v.getDateCompromis().atStartOfDay()
                                : null,
                docId
        );
    }
}
