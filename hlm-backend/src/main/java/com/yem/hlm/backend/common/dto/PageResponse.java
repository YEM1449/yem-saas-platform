package com.yem.hlm.backend.common.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Standardised pagination envelope returned by all paginated list endpoints.
 *
 * <pre>
 * {
 *   "content": [...],
 *   "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
 * }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        PageMeta page
) {
    public record PageMeta(int number, int size, long totalElements, int totalPages) {}

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new PageMeta(page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages())
        );
    }
}
