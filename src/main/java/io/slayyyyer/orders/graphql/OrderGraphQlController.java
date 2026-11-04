package io.slayyyyer.orders.graphql;

import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderStatus;
import io.slayyyyer.orders.domain.Product;
import io.slayyyyer.orders.repository.OrderPage;
import io.slayyyyer.orders.service.CreatedOrder;
import io.slayyyyer.orders.service.CustomerService;
import io.slayyyyer.orders.service.OrderService;
import io.slayyyyer.orders.service.ProductService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OrderGraphQlController {

    private final OrderService orders;
    private final CustomerService customers;
    private final ProductService products;

    public OrderGraphQlController(OrderService orders, CustomerService customers, ProductService products) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
    }

    @QueryMapping
    public Object customer(@Argument String id) {
        return customers.require(UUID.fromString(id));
    }

    @QueryMapping
    public Order order(@Argument String id) {
        return orders.require(UUID.fromString(id));
    }

    @QueryMapping
    public List<Product> products(@Argument String query, @Argument Integer limit) {
        int effectiveLimit = limit == null ? 20 : Math.min(limit, 100);
        if (effectiveLimit < 1) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return products.search(query, effectiveLimit, 0);
    }

    @QueryMapping
    public OrderConnection orders(
            @Argument String customerId,
            @Argument String status,
            @Argument Integer page,
            @Argument Integer size) {

        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 20 : size;

        OrderPage result = orders.byCustomer(
                UUID.fromString(customerId), parseStatus(status), effectivePage, effectiveSize);

        return new OrderConnection(
                result.orders(), result.page(), result.size(), result.totalCount(), result.hasNextPage());
    }

    @MutationMapping
    public CreatedOrder createOrder(@Argument CreateOrderInput input) {
        return orders.create(input.toCommand());
    }

    @MutationMapping
    public Order changeOrderStatus(@Argument String id, @Argument OrderStatus status) {
        return orders.transition(UUID.fromString(id), status);
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown order status: " + status);
        }
    }

    public record OrderConnection(
            List<Order> items, int page, int size, long totalCount, boolean hasNextPage) {
    }
}
