package com.yem.hlm.backend.portal.api.dto;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slim contract view for the buyer portal.
 */
public record PortalContractResponse(
        UUID id,
        String propertyRef,
        String propertyType,
        String projectName,
        SaleContractStatus status,
        BigDecimal agreedPrice,
        LocalDateTime signedAt
) {
    public static PortalContractResponse from(SaleContract c) {
        return new PortalContractResponse(
                c.getId(),
                c.getProperty().getReferenceCode(),
                c.getProperty().getType().name(),
                c.getProject().getName(),
                c.getStatus(),
                c.getAgreedPrice(),
                c.getSignedAt()
        );
    }
}
