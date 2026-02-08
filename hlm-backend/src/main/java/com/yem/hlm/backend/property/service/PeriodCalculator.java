package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.property.api.dto.DashboardPeriod;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Utility for calculating date ranges from DashboardPeriod presets.
 */
public class PeriodCalculator {

    /**
     * Calculates the date range for a given period preset.
     *
     * @param preset the period preset
     * @return DateRange with from and to dates
     */
    public static DateRange calculate(DashboardPeriod preset) {
        LocalDate today = LocalDate.now();

        return switch (preset) {
            case LAST_7_DAYS -> new DateRange(today.minusDays(7), today);
            case LAST_30_DAYS -> new DateRange(today.minusDays(30), today);
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case LAST_MONTH -> {
                LocalDate firstDayLastMonth = today.minusMonths(1).withDayOfMonth(1);
                LocalDate lastDayLastMonth = firstDayLastMonth.with(TemporalAdjusters.lastDayOfMonth());
                yield new DateRange(firstDayLastMonth, lastDayLastMonth);
            }
            case THIS_QUARTER -> {
                int currentMonth = today.getMonthValue();
                int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;
                LocalDate quarterStart = today.withMonth(quarterStartMonth).withDayOfMonth(1);
                yield new DateRange(quarterStart, today);
            }
            case LAST_QUARTER -> {
                int currentMonth = today.getMonthValue();
                int lastQuarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1 - 3;
                if (lastQuarterStartMonth <= 0) {
                    lastQuarterStartMonth += 12;
                }
                LocalDate lastQuarterStart = today.minusMonths(3).withMonth(lastQuarterStartMonth).withDayOfMonth(1);
                LocalDate lastQuarterEnd = lastQuarterStart.plusMonths(3).minusDays(1);
                yield new DateRange(lastQuarterStart, lastQuarterEnd);
            }
            case YTD -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
        };
    }

    /**
     * Date range record with from and to dates.
     */
    public record DateRange(LocalDate from, LocalDate to) {
    }
}
