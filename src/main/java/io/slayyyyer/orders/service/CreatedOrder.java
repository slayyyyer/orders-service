package io.slayyyyer.orders.service;

import io.slayyyyer.orders.domain.Order;

/**
 * @param replayed true when an order with this idempotency key already existed, so the
 *                 caller's retry is answered with the original order instead of a new one
 */
public record CreatedOrder(Order order, boolean replayed) {
}
