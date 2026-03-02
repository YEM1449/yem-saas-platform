package com.yem.hlm.backend.commission.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create / update request for a commission rule.
 *
 * @param projectId     optional — null means tenant-wide default rule
 * @param ratePercent   commission rate as a percentage (e.g. 2.5 = 2.5 %)
 * @param fixedAmount   optional fixed amount added on top of rate commission
 * @param effectiveFrom start date (inclusive)
 * @param effectiveTo   end date (inclusive); null = "until further notice"
 */
public record CommissionRuleRequest(
        UUID projectId,
        @NotNull @DecimalMin(value = "0.00", message = "ratePercent must be >= 0")
        BigDecimal ratePercent,
        BigDecimal fixedAmount,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
