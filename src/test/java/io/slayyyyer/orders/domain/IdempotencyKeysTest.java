package io.slayyyyer.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeysTest {

    @Test
    void acceptsAnOpaqueKeyAndTrimsIt() {
        assertThat(IdempotencyKeys.requireValid("  order:2026-11-04:abc123  "))
                .isEqualTo("order:2026-11-04:abc123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "", "has space inside", "semi;colon;not.allowed"})
    void rejectsMalformedKeys(String key) {
        assertThatThrownBy(() -> IdempotencyKeys.requireValid(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> IdempotencyKeys.requireValid(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsKeyLongerThanTheIndexCanHold() {
        String tooLong = "k".repeat(129);
        assertThatThrownBy(() -> IdempotencyKeys.requireValid(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between");
    }
}
