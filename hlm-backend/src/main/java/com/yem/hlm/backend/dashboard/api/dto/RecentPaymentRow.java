package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row in the recentPayments list on the receivables dashboard. */
public record RecentPaymentRow(
        UUID paymentId,
        BigDecimal amountReceived,
        LocalDate receivedAt,
        String method,
        String projectName,
        String propertyRef,
        String agentEmail
) {}
