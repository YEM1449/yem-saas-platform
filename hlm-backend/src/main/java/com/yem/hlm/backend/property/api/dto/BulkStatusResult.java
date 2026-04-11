package com.yem.hlm.backend.property.api.dto;

/**
 * Response for {@code PATCH /api/properties/bulk-status}.
 *
 * @param updated number of properties whose status was changed
 * @param skipped number of properties skipped (RESERVED/SOLD or not found)
 */
public record BulkStatusResult(int updated, int skipped) {}
