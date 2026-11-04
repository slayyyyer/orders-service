package io.slayyyyer.orders.repository.jdbc;

import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.repository.OutboxRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class OutboxRepositoryJdbc implements OutboxRepository {

    // Spring Boot 4 auto-configures a Jackson 3 mapper (tools.jackson). The Jackson 2
    // ObjectMapper that this class used to inject only exists here as a transitive
    // dependency of springdoc, so there was no bean of that type and the application
    // context failed to start — invisible to unit tests, fatal in the integration run.
    //
    // The outbox serialises one flat map of primitives, so owning a mapper is simpler than
    // injecting the application's JSON configuration, and it removes the wiring dependency
    // entirely.
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public OutboxRepositoryJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(Order order, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id().toString());
        payload.put("customerId", order.customerId().toString());
        payload.put("status", order.status().name());
        payload.put("totalCents", order.totalCents());
        payload.put("currency", order.currency());
        payload.put("occurredAt", order.createdAt().toString());

        jdbc.sql("""
                insert into outbox_events (aggregate_id, event_type, payload)
                values (:aggregateId, :eventType, cast(:payload as jsonb))
                """)
                .param("aggregateId", order.id())
                .param("eventType", eventType)
                .param("payload", JSON.writeValueAsString(payload))
                .update();
    }
}
