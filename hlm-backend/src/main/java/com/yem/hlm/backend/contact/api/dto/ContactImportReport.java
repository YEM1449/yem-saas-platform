package com.yem.hlm.backend.contact.api.dto;

import java.util.List;

/**
 * Result of a CSV contact import ({@code POST /api/contacts/import}).
 *
 * <p>In dry-run mode nothing is persisted; the report previews what a real import would do.
 * {@code duplicates} and {@code errors} are capped at {@link #MAX_ISSUES} entries each
 * ({@code truncated} flips to true when the cap is hit) so the payload stays bounded.
 */
public record ContactImportReport(
        boolean dryRun,
        int totalRows,
        int created,
        int duplicateCount,
        int errorCount,
        boolean truncated,
        List<String> ignoredColumns,
        List<RowIssue> duplicates,
        List<RowIssue> errors
) {
    public static final int MAX_ISSUES = 100;

    /** One problematic CSV row. {@code line} is 1-based and counts the header as line 1. */
    public record RowIssue(int line, String identity, String reason) {}
}
