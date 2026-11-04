package io.slayyyyer.orders.service;

import io.slayyyyer.orders.domain.IdempotencyKeys;
import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderLine;
import io.slayyyyer.orders.domain.OrderPricing;
import io.slayyyyer.orders.domain.OrderStatus;
import io.slayyyyer.orders.domain.Product;
import io.slayyyyer.orders.repository.CustomerRepository;
import io.slayyyyer.orders.repository.OrderPage;
import io.slayyyyer.orders.repository.OrderRepository;
import io.slayyyyer.orders.repository.OutboxRepository;
import io.slayyyyer.orders.repository.ProductRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OutboxRepository outbox;
    private final Clock clock;

    public OrderService(
            OrderRepository orders,
            CustomerRepository customers,
            ProductRepository products,
            OutboxRepository outbox,
            Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Creates an order, or returns the original one when the idempotency key has been
     * seen before. Prices always come from the database; the request only chooses
     * products and quantities.
     */
    @Transactional
    public CreatedOrder create(CreateOrderCommand command) {
        String key = IdempotencyKeys.requireValid(command.idempotencyKey());

        Optional<UUID> existingId = orders.findIdByIdempotencyKey(key);
        if (existingId.isPresent()) {
            return replay(existingId.get());
        }

        if (command.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        customers.findById(command.customerId())
                .orElseThrow(() -> new NotFoundException("customer", command.customerId()));

        if (command.items().isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one item");
        }

        Map<UUID, Integer> quantities = mergeQuantities(command.items());
        Map<UUID, Product> priced = products.findAllByIds(quantities.keySet());
        for (UUID productId : quantities.keySet()) {
            if (!priced.containsKey(productId)) {
                throw new NotFoundException("product", productId);
            }
        }

        List<OrderLine> lines = new ArrayList<>(quantities.size());
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Product product = priced.get(entry.getKey());
            lines.add(new OrderLine(
                    product.id(),
                    product.sku(),
                    product.name(),
                    entry.getValue(),
                    product.unitPriceCents()));
        }

        Order order = new Order(
                UUID.randomUUID(),
                command.customerId(),
                OrderStatus.PENDING,
                "USD",
                OrderPricing.total(lines),
                clock.instant(),
                lines);

        try {
            orders.insert(order, key);
        } catch (DuplicateKeyException race) {
            // Two concurrent requests carried the same key. The unique index won; answer
            // the loser with the winner's order rather than a 500.
            return orders.findIdByIdempotencyKey(key)
                    .map(this::replay)
                    .orElseThrow(() -> new ConflictException("concurrent order creation for this idempotency key"));
        }

        // Same transaction as the insert: no event without an order, no order without an event.
        outbox.append(order, "order.created");

        return new CreatedOrder(order, false);
    }

    @Transactional(readOnly = true)
    public Order require(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("order", id));
    }

    @Transactional(readOnly = true)
    public OrderPage byCustomer(UUID customerId, OrderStatus status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("customer", customerId));
        return orders.findByCustomer(customerId, status, page, size);
    }

    @Transactional
    public Order transition(UUID id, OrderStatus next) {
        Order current = require(id);
        if (!current.status().canTransitionTo(next)) {
            throw new ConflictException(
                    "cannot move order from " + current.status() + " to " + next);
        }
        orders.updateStatus(id, next);
        Order updated = require(id);
        outbox.append(updated, "order.status-changed");
        return updated;
    }

    private CreatedOrder replay(UUID id) {
        Order existing = orders.findById(id)
                .orElseThrow(() -> new NotFoundException("order", id));
        return new CreatedOrder(existing, true);
    }

    private Map<UUID, Integer> mergeQuantities(List<CreateOrderCommand.RequestedItem> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        Map<UUID, Integer> seen = new HashMap<>();
        for (CreateOrderCommand.RequestedItem item : items) {
            if (item == null || item.productId() == null) {
                throw new IllegalArgumentException("each item needs a productId");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (seen.put(item.productId(), item.quantity()) != null) {
                quantities.merge(item.productId(), item.quantity(), Math::addExact);
            } else {
                quantities.put(item.productId(), item.quantity());
            }
        }
        return quantities;
    }
}
