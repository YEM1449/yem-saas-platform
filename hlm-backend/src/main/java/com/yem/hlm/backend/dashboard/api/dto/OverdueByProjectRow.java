package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row in the overdueByProject list: project + total overdue amount. */
public record OverdueByProjectRow(UUID projectId, String projectName, BigDecimal overdueAmount) {}
