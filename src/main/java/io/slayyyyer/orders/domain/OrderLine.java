package io.slayyyyer.orders.domain;

import java.util.UUID;

/**
 * One line of an order. Money is held in integer minor units (cents) so totals are
 * exact; the database stores the same representation.
 */
public record OrderLine(UUID productId, String sku, String name, int quantity, long unitPriceCents) {

    public OrderLine {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("unitPriceCents must not be negative");
        }
    }

    public long lineTotalCents() {
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }
}
