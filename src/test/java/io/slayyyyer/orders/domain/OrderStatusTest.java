package io.slayyyyer.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void pendingCanBePaidOrCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void paidCanOnlyShip() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesGoNowhere() {
        for (OrderStatus terminal : new OrderStatus[] {OrderStatus.SHIPPED, OrderStatus.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).isFalse();
            }
        }
    }

    @Test
    void selfTransitionAndNullAreRejected() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(null)).isFalse();
    }
}
