package com.yem.hlm.backend.property.api.dto;

/**
 * Predefined period presets for dashboard queries.
 * Provides convenient shortcuts for common time ranges.
 */
public enum DashboardPeriod {
    /**
     * Last 7 days from today.
     */
    LAST_7_DAYS,

    /**
     * Last 30 days from today.
     */
    LAST_30_DAYS,

    /**
     * Current calendar month (from 1st to today).
     */
    THIS_MONTH,

    /**
     * Previous calendar month (complete month).
     */
    LAST_MONTH,

    /**
     * Current calendar quarter.
     */
    THIS_QUARTER,

    /**
     * Previous calendar quarter.
     */
    LAST_QUARTER,

    /**
     * Year-to-date (from Jan 1 to today).
     */
    YTD
}
