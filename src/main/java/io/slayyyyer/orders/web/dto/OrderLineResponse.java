package io.slayyyyer.orders.web.dto;

import io.slayyyyer.orders.domain.OrderLine;
import java.util.UUID;

public record OrderLineResponse(
        UUID productId, String sku, String name, int quantity, long unitPriceCents, long lineTotalCents) {

    public static OrderLineResponse from(OrderLine line) {
        return new OrderLineResponse(
                line.productId(),
                line.sku(),
                line.name(),
                line.quantity(),
                line.unitPriceCents(),
                line.lineTotalCents());
    }
}
