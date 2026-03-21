package com.yem.hlm.backend.commission.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Response DTO for a commission rule. */
public record CommissionRuleResponse(
        UUID id,
        UUID societeId,
        UUID projectId,
        String projectName,
        BigDecimal ratePercent,
        BigDecimal fixedAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
