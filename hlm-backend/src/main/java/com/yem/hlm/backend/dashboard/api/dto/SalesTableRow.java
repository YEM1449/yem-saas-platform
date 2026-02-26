package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row in the paginated sales drill-down table. */
public record SalesTableRow(
        UUID id,
        LocalDateTime signedAt,
        String projectName,
        String propertyRef,
        String buyerName,
        String agentEmail,
        BigDecimal amount
) {}
