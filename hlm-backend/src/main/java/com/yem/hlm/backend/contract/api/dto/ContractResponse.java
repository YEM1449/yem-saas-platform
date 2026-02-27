package com.yem.hlm.backend.contract.api.dto;

import com.yem.hlm.backend.contract.domain.BuyerType;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API response DTO for a {@link SaleContract}.
 * All KPI-relevant fields are exposed for analytics consumers.
 *
 * <p>Buyer snapshot fields ({@code buyerType}, {@code buyerDisplayName}, etc.) are null
 * for DRAFT contracts and populated immutably once the contract is SIGNED.
 */
public record ContractResponse(
        UUID id,
        UUID tenantId,
        UUID projectId,
        String projectName,
        UUID propertyId,
        UUID buyerContactId,
        UUID agentId,
        SaleContractStatus status,
        BigDecimal agreedPrice,
        BigDecimal listPrice,
        UUID sourceDepositId,
        LocalDateTime createdAt,
        LocalDateTime signedAt,
        LocalDateTime canceledAt,
        // ── Buyer snapshot (populated at SIGNED time, immutable) ──
        BuyerType buyerType,
        String buyerDisplayName,
        String buyerPhone,
        String buyerEmail,
        String buyerIce,
        String buyerAddress
) {
    public static ContractResponse from(SaleContract c) {
        return new ContractResponse(
                c.getId(),
                c.getTenant().getId(),
                c.getProject().getId(),
                c.getProject().getName(),
                c.getProperty().getId(),
                c.getBuyerContact().getId(),
                c.getAgent().getId(),
                c.getStatus(),
                c.getAgreedPrice(),
                c.getListPrice(),
                c.getSourceDepositId(),
                c.getCreatedAt(),
                c.getSignedAt(),
                c.getCanceledAt(),
                c.getBuyerType(),
                c.getBuyerDisplayName(),
                c.getBuyerPhone(),
                c.getBuyerEmail(),
                c.getBuyerIce(),
                c.getBuyerAddress()
        );
    }
}
