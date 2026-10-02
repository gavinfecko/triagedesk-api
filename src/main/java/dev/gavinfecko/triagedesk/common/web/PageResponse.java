package dev.gavinfecko.triagedesk.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Every list endpoint answers {@code {"items": [...], "page": {...}}} (ARCHITECTURE.md §7). */
public record PageResponse<T>(List<T> items, PageMeta page) {

    public static final int MAX_SIZE = 100;

    public record PageMeta(int number, int size, long totalElements, int totalPages) {}

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    /** A page request with the size clamped to 1..100 and a negative page treated as 0. */
    public static PageRequest request(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), sort);
    }
}
