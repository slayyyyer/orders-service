package io.slayyyyer.orders.domain;

import java.util.List;

/**
 * Money arithmetic for orders. Prices always come from the database, never from the
 * client, so a quoted price cannot be tampered with; this class only does the sums.
 */
public final class OrderPricing {

    private OrderPricing() {
    }

    public static long lineTotal(int quantity, long unitPriceCents) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("unit price must not be negative");
        }
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }

    public static long total(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        long total = 0L;
        for (OrderLine line : lines) {
            total = Math.addExact(total, line.lineTotalCents());
        }
        return total;
    }
}
