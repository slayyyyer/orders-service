package io.slayyyyer.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPricingTest {

    private static final UUID PRODUCT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("line total is quantity times unit price, in cents")
    void lineTotal() {
        assertThat(OrderPricing.lineTotal(3, 12499)).isEqualTo(37497);
        assertThat(OrderPricing.lineTotal(1, 0)).isZero();
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> OrderPricing.lineTotal(0, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
        assertThatThrownBy(() -> OrderPricing.lineTotal(-2, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> OrderPricing.lineTotal(1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void totalsAllLines() {
        List<OrderLine> lines = List.of(
                new OrderLine(PRODUCT, "SKU-1", "Keyboard", 1, 12499),
                new OrderLine(PRODUCT, "SKU-2", "Desk mat", 2, 3900));

        assertThat(OrderPricing.total(lines)).isEqualTo(20299);
    }

    @Test
    void rejectsEmptyOrder() {
        assertThatThrownBy(() -> OrderPricing.total(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
        assertThatThrownBy(() -> OrderPricing.total(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsLoudlyOnOverflowInsteadOfWrapping() {
        assertThatThrownBy(() -> OrderPricing.lineTotal(Integer.MAX_VALUE, Long.MAX_VALUE / 2))
                .isInstanceOf(ArithmeticException.class);
    }
}
