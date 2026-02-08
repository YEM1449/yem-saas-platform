package com.yem.hlm.backend.deposit.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record DepositReportResponse(
        List<DepositResponse> items,
        long count,
        BigDecimal totalAmount,
        List<DepositReportByAgent> byAgent
) {}
