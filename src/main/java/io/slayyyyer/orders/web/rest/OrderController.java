package io.slayyyyer.orders.web.rest;

import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.domain.OrderStatus;
import io.slayyyyer.orders.repository.OrderPage;
import io.slayyyyer.orders.service.CreatedOrder;
import io.slayyyyer.orders.service.OrderService;
import io.slayyyyer.orders.web.dto.CreateOrderRequest;
import io.slayyyyer.orders.web.dto.OrderResponse;
import io.slayyyyer.orders.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Idempotent creation. The first request returns 201 with a Location header; a replay
     * with the same key returns 200 and the original order.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        CreatedOrder created = orders.create(request.toCommand(idempotencyKey));
        Order order = created.order();

        if (created.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(OrderResponse.from(order));
        }

        return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orders.require(id));
    }

    @GetMapping
    public PagedResponse<OrderResponse> byCustomer(
            @RequestParam UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OrderPage result = orders.byCustomer(customerId, parseStatus(status), page, size);
        return PagedResponse.of(
                result.orders().stream().map(OrderResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalCount(),
                result.hasNextPage());
    }

    @PostMapping("/{id}/status")
    public OrderResponse transition(@PathVariable UUID id, @RequestParam String status) {
        return OrderResponse.from(orders.transition(id, parseStatus(status)));
    }

    static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown order status: " + status);
        }
    }

    static HttpStatus noContent() {
        return HttpStatus.NO_CONTENT;
    }

    static HttpHeaders headers() {
        return new HttpHeaders();
    }
}
