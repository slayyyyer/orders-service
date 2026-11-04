package io.slayyyyer.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slayyyyer.orders.domain.Customer;
import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderLine;
import io.slayyyyer.orders.domain.OrderStatus;
import io.slayyyyer.orders.domain.Product;
import io.slayyyyer.orders.repository.CustomerRepository;
import io.slayyyyer.orders.repository.OrderPage;
import io.slayyyyer.orders.repository.OrderRepository;
import io.slayyyyer.orders.repository.OutboxRepository;
import io.slayyyyer.orders.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String KEY = "idem-key-0001";
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    private static final Customer ADA =
            new Customer(
                    UUID.fromString("11111111-1111-4111-8111-111111111111"),
                    "ada@example.com",
                    "Ada Lovelace",
                    Instant.parse("2026-01-05T09:00:00Z"));

    private static final Product KEYBOARD =
            new Product(
                    UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                    "KEY-MECH-01",
                    "Mechanical keyboard",
                    12499);

    @Mock private OrderRepository orders;
    @Mock private CustomerRepository customers;
    @Mock private ProductRepository products;
    @Mock private OutboxRepository outbox;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orders, customers, products, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("prices come from the database, not from the request")
    void createsOrderUsingStoredPrices() {
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));
        when(products.findAllByIds(any())).thenReturn(Map.of(KEYBOARD.id(), KEYBOARD));

        CreatedOrder created = service.create(command(ADA.id(), List.of(item(KEYBOARD.id(), 2))));

        assertThat(created.replayed()).isFalse();
        assertThat(created.order().totalCents()).isEqualTo(24998);
        assertThat(created.order().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.order().createdAt()).isEqualTo(NOW);
        assertThat(created.order().items()).singleElement()
                .satisfies(line -> assertThat(line.unitPriceCents()).isEqualTo(12499));

        verify(orders).insert(any(Order.class), eq(KEY));
        verify(outbox).append(any(Order.class), eq("order.created"));
    }

    @Test
    @DisplayName("a repeated idempotency key returns the original order and writes nothing")
    void replayReturnsExistingOrder() {
        UUID existingId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        Order existing = order(existingId, OrderStatus.PAID);

        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.of(existingId));
        when(orders.findById(existingId)).thenReturn(Optional.of(existing));

        CreatedOrder created = service.create(command(ADA.id(), List.of(item(KEYBOARD.id(), 1))));

        assertThat(created.replayed()).isTrue();
        assertThat(created.order().id()).isEqualTo(existingId);
        verify(orders, never()).insert(any(), any());
        verify(outbox, never()).append(any(), any());
    }

    @Test
    void unknownCustomerIsNotFound() {
        UUID customerId = UUID.randomUUID();
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(customerId, List.of(item(KEYBOARD.id(), 1)))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void unknownProductIsNotFound() {
        UUID productId = UUID.randomUUID();
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));
        when(products.findAllByIds(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.create(command(ADA.id(), List.of(item(productId, 1)))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("product");
    }

    @Test
    @DisplayName("duplicate lines for the same product are merged, not double-priced")
    void mergesDuplicateProductLines() {
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));
        when(products.findAllByIds(any())).thenReturn(Map.of(KEYBOARD.id(), KEYBOARD));

        CreatedOrder created = service.create(command(
                ADA.id(), List.of(item(KEYBOARD.id(), 1), item(KEYBOARD.id(), 2))));

        assertThat(created.order().items()).singleElement()
                .satisfies(line -> {
                    assertThat(line.quantity()).isEqualTo(3);
                    assertThat(line.lineTotalCents()).isEqualTo(37497);
                });
    }

    @Test
    void rejectsEmptyItemList() {
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));

        assertThatThrownBy(() -> service.create(command(ADA.id(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void rejectsInvalidIdempotencyKeyBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.create(new CreateOrderCommand(
                        ADA.id(), List.of(item(KEYBOARD.id(), 1)), "bad key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        when(orders.findIdByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));

        assertThatThrownBy(() -> service.create(command(ADA.id(), List.of(item(KEYBOARD.id(), 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void transitionFollowsTheStateMachine() {
        UUID id = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        Order pending = order(id, OrderStatus.PENDING);
        Order paid = order(id, OrderStatus.PAID);

        when(orders.findById(id)).thenReturn(Optional.of(pending), Optional.of(paid));

        Order updated = service.transition(id, OrderStatus.PAID);

        assertThat(updated.status()).isEqualTo(OrderStatus.PAID);
        verify(orders).updateStatus(id, OrderStatus.PAID);
        verify(outbox).append(any(Order.class), eq("order.status-changed"));
    }

    @Test
    void transitionRejectsIllegalJump() {
        UUID id = UUID.randomUUID();
        Order pending = order(id, OrderStatus.PENDING);
        when(orders.findById(id)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.transition(id, OrderStatus.SHIPPED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void pagingBoundsAreValidated() {
        assertThatThrownBy(() -> service.byCustomer(ADA.id(), null, -1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> service.byCustomer(ADA.id(), null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> service.byCustomer(ADA.id(), null, 0, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void byCustomerDelegatesToTheRepositoryWithTheFilter() {
        when(customers.findById(ADA.id())).thenReturn(Optional.of(ADA));
        when(orders.findByCustomer(ADA.id(), OrderStatus.PAID, 0, 20))
                .thenReturn(new OrderPage(List.of(order(UUID.randomUUID(), OrderStatus.PAID)), 0, 20, 1));

        OrderPage page = service.byCustomer(ADA.id(), OrderStatus.PAID, 0, 20);

        assertThat(page.orders()).hasSize(1);
        assertThat(page.hasNextPage()).isFalse();
        verify(orders).findByCustomer(ADA.id(), OrderStatus.PAID, 0, 20);
        verify(orders, never()).findByCustomer(eq(ADA.id()), eq(null), anyInt(), anyInt());
    }

    private static CreateOrderCommand command(
            UUID customerId, List<CreateOrderCommand.RequestedItem> items) {
        return new CreateOrderCommand(customerId, items, KEY);
    }

    private static CreateOrderCommand.RequestedItem item(UUID productId, int quantity) {
        return new CreateOrderCommand.RequestedItem(productId, quantity);
    }

    private static Order order(UUID id, OrderStatus status) {
        return new Order(
                id,
                ADA.id(),
                status,
                "USD",
                12499,
                NOW,
                List.of(new OrderLine(KEYBOARD.id(), KEYBOARD.sku(), KEYBOARD.name(), 1, 12499)));
    }
}
