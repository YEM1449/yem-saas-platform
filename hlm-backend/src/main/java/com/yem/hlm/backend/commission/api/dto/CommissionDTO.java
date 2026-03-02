package com.yem.hlm.backend.commission.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Result of a commission calculation for one signed contract.
 */
public record CommissionDTO(
        UUID contractId,
        UUID agentId,
        String agentEmail,
        String projectName,
        String propertyRef,
        LocalDateTime signedAt,
        BigDecimal agreedPrice,
        /** Commission rate applied (%). */
        BigDecimal ratePercent,
        /** Fixed component applied (0 if none). */
        BigDecimal fixedAmount,
        /** Total commission = agreedPrice * ratePercent / 100 + fixedAmount. */
        BigDecimal commissionAmount
) {}
