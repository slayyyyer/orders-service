package io.slayyyyer.orders.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        long totalCents,
        Instant createdAt,
        List<OrderLine> items) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(status, "status");
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        if (totalCents < 0) {
            throw new IllegalArgumentException("totalCents must not be negative");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
