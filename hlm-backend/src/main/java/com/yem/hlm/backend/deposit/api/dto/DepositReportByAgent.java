package com.yem.hlm.backend.deposit.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositReportByAgent(
        UUID agentId,
        String agentEmail,
        long count,
        BigDecimal totalAmount
) {}
