package io.slayyyyer.orders.repository;

import io.slayyyyer.orders.domain.Order;

public interface OutboxRepository {

    /**
     * Appended inside the same transaction as the order it describes. Nothing publishes
     * here; a future relay would read unpublished rows and push them to Pub/Sub.
     */
    void append(Order order, String eventType);
}
