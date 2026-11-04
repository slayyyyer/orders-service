package io.slayyyyer.orders.web.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> items, int page, int size, long totalCount, boolean hasNextPage) {

    public static <T> PagedResponse<T> of(
            List<T> items, int page, int size, long totalCount, boolean hasNextPage) {
        return new PagedResponse<>(List.copyOf(items), page, size, totalCount, hasNextPage);
    }
}
