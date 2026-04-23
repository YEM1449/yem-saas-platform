package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record UserQuotaRequest(
        /** "YYYY-MM" e.g. "2026-04" */
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        BigDecimal caCible,
        Long ventesCountCible
) {}
