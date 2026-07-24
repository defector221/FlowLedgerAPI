package com.flowledger.common.dto;

import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** In-memory slice for reports/ledgers built as full lists. */
    public static <T> PageResponse<T> slice(List<T> all, Pageable pageable) {
        int total = all.size();
        int pageSize = Math.max(1, pageable.getPageSize());
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageSize, total);
        List<T> content = start >= total ? List.of() : all.subList(start, end);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(
                content.isEmpty() ? Collections.emptyList() : List.copyOf(content),
                pageNumber,
                pageSize,
                total,
                totalPages);
    }

    /** For EntityManager-based queries that already fetched a single page plus a separate count. */
    public static <T> PageResponse<T> of(List<T> content, Pageable pageable, long total) {
        int pageSize = Math.max(1, pageable.getPageSize());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(content, Math.max(0, pageable.getPageNumber()), pageSize, total, totalPages);
    }
}
