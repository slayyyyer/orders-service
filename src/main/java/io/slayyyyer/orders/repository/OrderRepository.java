package io.slayyyyer.orders.repository;

import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Optional<Order> findById(UUID id);

    /**
     * Look up an existing order by its client-supplied idempotency key. This is the
     * read side of the replay path: a retried request must return the original order.
     */
    Optional<UUID> findIdByIdempotencyKey(String idempotencyKey);

    OrderPage findByCustomer(UUID customerId, OrderStatus status, int page, int size);

    void insert(Order order, String idempotencyKey);

    void updateStatus(UUID id, OrderStatus status);
}
