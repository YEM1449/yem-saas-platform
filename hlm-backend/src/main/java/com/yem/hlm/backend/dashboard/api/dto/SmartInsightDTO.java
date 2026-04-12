package com.yem.hlm.backend.dashboard.api.dto;

public record SmartInsightDTO(
        String id,
        InsightType type,
        InsightPriority priority,
        String title,
        String description,
        String actionLabel,
        String actionRoute
) {

    public enum InsightType { OPPORTUNITY, RISK, TREND, INFO }

    public enum InsightPriority { HIGH, MEDIUM, LOW }
}
