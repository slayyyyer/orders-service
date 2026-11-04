package io.slayyyyer.orders.web.dto;

import io.slayyyyer.orders.domain.Product;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, long unitPriceCents) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(), product.sku(), product.name(), product.unitPriceCents());
    }
}
