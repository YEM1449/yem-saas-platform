package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;

public record VenteReceivablesSummary(
    BigDecimal totalOutstanding,  // sum of all unpaid échéances
    BigDecimal totalOverdue,      // sum of unpaid échéances with dateEcheance < today
    BigDecimal current,           // sum of unpaid échéances with dateEcheance >= today
    BigDecimal bucket1to30,       // overdue 1–30 days
    BigDecimal bucket31to60,
    BigDecimal bucket61to90,
    BigDecimal bucketOver90
) {}
