package io.slayyyyer.orders.domain;

import java.util.Set;

/**
 * Order lifecycle. Transitions live here rather than in the service so the rules
 * are unit-testable without a database.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    CANCELLED;

    private static final Set<OrderStatus> TERMINAL = Set.of(SHIPPED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPED;
            case SHIPPED, CANCELLED -> false;
        };
    }
}
