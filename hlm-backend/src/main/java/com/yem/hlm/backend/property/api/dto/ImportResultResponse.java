package com.yem.hlm.backend.property.api.dto;

import java.util.List;

/**
 * Result of a CSV property import operation.
 *
 * @param imported number of properties successfully imported
 * @param errors   row-level validation errors (empty if fully successful)
 */
public record ImportResultResponse(
        int imported,
        List<RowError> errors
) {
    public record RowError(int row, String message) {}
}
