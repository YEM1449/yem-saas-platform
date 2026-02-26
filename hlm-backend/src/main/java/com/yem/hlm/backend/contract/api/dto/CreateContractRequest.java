package com.yem.hlm.backend.contract.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a new sales contract in DRAFT status.
 *
 * @param projectId       project the contract belongs to (must be ACTIVE)
 * @param propertyId      property being sold (must belong to the project)
 * @param buyerContactId  buyer contact (must exist in the same tenant)
 * @param agentId         responsible agent — required for ADMIN/MANAGER; for AGENT callers
 *                        this is overridden to the caller's own ID (any provided value that
 *                        differs from the caller will be rejected with 400)
 * @param agreedPrice     negotiated sale price (mandatory, > 0)
 * @param listPrice       original list price (optional — enables discount KPIs)
 * @param sourceDepositId optional reference to the CONFIRMED deposit this contract originates from
 */
public record CreateContractRequest(
        @NotNull UUID projectId,
        @NotNull UUID propertyId,
        @NotNull UUID buyerContactId,
        UUID agentId,
        @NotNull @DecimalMin(value = "0.01", message = "agreedPrice must be > 0") BigDecimal agreedPrice,
        BigDecimal listPrice,
        UUID sourceDepositId
) {}
