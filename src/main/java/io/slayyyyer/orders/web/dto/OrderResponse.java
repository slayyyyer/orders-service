package io.slayyyyer.orders.web.dto;

import io.slayyyyer.orders.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        String currency,
        long totalCents,
        Instant createdAt,
        List<OrderLineResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.status().name(),
                order.currency(),
                order.totalCents(),
                order.createdAt(),
                order.items().stream().map(OrderLineResponse::from).toList());
    }
}
