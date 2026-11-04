package io.slayyyyer.orders.repository.jdbc;

import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderLine;
import io.slayyyyer.orders.domain.OrderStatus;
import io.slayyyyer.orders.repository.OrderPage;
import io.slayyyyer.orders.repository.OrderRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepositoryJdbc implements OrderRepository {

    private static final String ORDER_COLUMNS = "id, customer_id, status, currency, total_cents, created_at";

    private final JdbcClient jdbc;

    public OrderRepositoryJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        Optional<OrderRow> row = jdbc.sql("select " + ORDER_COLUMNS + " from orders where id = :id")
                .param("id", id)
                .query(OrderRepositoryJdbc::mapOrderRow)
                .optional();

        return row.map(orderRow -> new Order(
                orderRow.id(),
                orderRow.customerId(),
                orderRow.status(),
                orderRow.currency(),
                orderRow.totalCents(),
                orderRow.createdAt(),
                loadItems(List.of(orderRow.id()))));
    }

    @Override
    public Optional<UUID> findIdByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select id from orders where idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(UUID.class)
                .optional();
    }

    @Override
    public OrderPage findByCustomer(UUID customerId, OrderStatus status, int page, int size) {
        String filter = status == null ? "" : " and status = :status";

        Long total = jdbc.sql("select count(*) from orders where customer_id = :customerId" + filter)
                .param("customerId", customerId)
                .param("status", status == null ? null : status.name())
                .query(Long.class)
                .single();

        List<OrderRow> rows = jdbc.sql("select " + ORDER_COLUMNS + """
                  from orders
                 where customer_id = :customerId""" + filter + """
                 order by created_at desc, id
                 limit :limit offset :offset
                """)
                .param("customerId", customerId)
                .param("status", status == null ? null : status.name())
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(OrderRepositoryJdbc::mapOrderRow)
                .list();

        Map<UUID, List<OrderLine>> itemsByOrder = loadItemsByOrder(rows.stream().map(OrderRow::id).toList());

        List<Order> orders = new ArrayList<>(rows.size());
        for (OrderRow row : rows) {
            orders.add(new Order(
                    row.id(),
                    row.customerId(),
                    row.status(),
                    row.currency(),
                    row.totalCents(),
                    row.createdAt(),
                    itemsByOrder.getOrDefault(row.id(), List.of())));
        }

        return new OrderPage(orders, page, size, total == null ? 0L : total);
    }

    @Override
    public void insert(Order order, String idempotencyKey) {
        jdbc.sql("""
                insert into orders
                    (id, customer_id, status, currency, total_cents, idempotency_key, created_at, updated_at)
                values
                    (:id, :customerId, :status, :currency, :totalCents, :idempotencyKey, :createdAt, :createdAt)
                """)
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("status", order.status().name())
                .param("currency", order.currency())
                .param("totalCents", order.totalCents())
                .param("idempotencyKey", idempotencyKey)
                .param("createdAt", java.sql.Timestamp.from(order.createdAt()))
                .update();

        for (OrderLine line : order.items()) {
            jdbc.sql("""
                    insert into order_items (id, order_id, product_id, quantity, unit_price_cents)
                    values (:id, :orderId, :productId, :quantity, :unitPriceCents)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("orderId", order.id())
                    .param("productId", line.productId())
                    .param("quantity", line.quantity())
                    .param("unitPriceCents", line.unitPriceCents())
                    .update();
        }
    }

    @Override
    public void updateStatus(UUID id, OrderStatus status) {
        jdbc.sql("update orders set status = :status, updated_at = now() where id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }

    private List<OrderLine> loadItems(List<UUID> orderIds) {
        return loadItemsByOrder(orderIds).getOrDefault(orderIds.getFirst(), List.of());
    }

    private Map<UUID, List<OrderLine>> loadItemsByOrder(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<OrderLine>> byOrder = new HashMap<>();
        jdbc.sql("""
                select i.order_id, i.product_id, p.sku, p.name, i.quantity, i.unit_price_cents
                  from order_items i
                  join products p on p.id = i.product_id
                 where i.order_id in (:orderIds)
                 order by i.order_id, p.name
                """)
                .param("orderIds", orderIds)
                .query((rs, rowNum) -> {
                    UUID orderId = rs.getObject("order_id", UUID.class);
                    OrderLine line = new OrderLine(
                            rs.getObject("product_id", UUID.class),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getLong("unit_price_cents"));
                    byOrder.computeIfAbsent(orderId, key -> new ArrayList<>()).add(line);
                    return line;
                })
                .list();
        return byOrder;
    }

    private static OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
        return new OrderRow(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("currency"),
                rs.getLong("total_cents"),
                rs.getTimestamp("created_at").toInstant());
    }

    private record OrderRow(
            UUID id,
            UUID customerId,
            OrderStatus status,
            String currency,
            long totalCents,
            java.time.Instant createdAt) {
    }
}
