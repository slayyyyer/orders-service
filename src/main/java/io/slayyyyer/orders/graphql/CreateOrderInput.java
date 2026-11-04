package io.slayyyyer.orders.graphql;

import io.slayyyyer.orders.service.CreateOrderCommand;
import java.util.List;
import java.util.UUID;

public record CreateOrderInput(UUID customerId, List<OrderItemInput> items, String idempotencyKey) {

    public record OrderItemInput(UUID productId, int quantity) {
    }

    public CreateOrderCommand toCommand() {
        List<OrderItemInput> safeItems = items == null ? List.of() : items;
        return new CreateOrderCommand(
                customerId,
                safeItems.stream()
                        .map(item -> new CreateOrderCommand.RequestedItem(item.productId(), item.quantity()))
                        .toList(),
                idempotencyKey);
    }
}
