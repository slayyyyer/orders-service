package io.slayyyyer.orders.service;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(UUID customerId, List<RequestedItem> items, String idempotencyKey) {

    public CreateOrderCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record RequestedItem(UUID productId, int quantity) {
    }
}
