package io.slayyyyer.orders.domain;

import java.util.UUID;

public record Product(UUID id, String sku, String name, long unitPriceCents) {

    public Product {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("unitPriceCents must not be negative");
        }
    }
}
