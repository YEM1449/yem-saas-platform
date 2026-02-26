package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One data point in a daily trend series (amount per calendar day). */
public record DailySalesPoint(LocalDate date, BigDecimal amount) {}
