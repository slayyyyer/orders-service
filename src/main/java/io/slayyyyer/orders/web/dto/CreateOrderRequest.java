package io.slayyyyer.orders.web.dto;

import io.slayyyyer.orders.service.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "customerId is required") UUID customerId,
        @NotEmpty(message = "an order needs at least one item") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "productId is required") UUID productId,
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 1000, message = "quantity must be at most 1000") int quantity) {
    }

    public CreateOrderCommand toCommand(String idempotencyKey) {
        return new CreateOrderCommand(
                customerId,
                items.stream()
                        .map(item -> new CreateOrderCommand.RequestedItem(item.productId(), item.quantity()))
                        .toList(),
                idempotencyKey);
    }
}
