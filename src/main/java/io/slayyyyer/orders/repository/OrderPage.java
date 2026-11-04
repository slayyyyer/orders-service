package io.slayyyyer.orders.repository;

import io.slayyyyer.orders.domain.Order;
import java.util.List;

public record OrderPage(List<Order> orders, int page, int size, long totalCount) {

    public OrderPage {
        orders = List.copyOf(orders);
    }

    public boolean hasNextPage() {
        return (long) (page + 1) * size < totalCount;
    }
}
