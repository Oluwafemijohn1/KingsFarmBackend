package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Every list endpoint in this API returns this shape instead of Spring
 * Data's raw {@link Page} — keeps the JSON contract stable and simple for
 * the frontend regardless of how pagination is implemented underneath.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
