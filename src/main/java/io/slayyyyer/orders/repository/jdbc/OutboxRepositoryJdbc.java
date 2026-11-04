package io.slayyyyer.orders.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.slayyyyer.orders.domain.Order;
import io.slayyyyer.orders.repository.OutboxRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepositoryJdbc implements OutboxRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public OutboxRepositoryJdbc(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
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
                .param("payload", toJson(payload))
                .update();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise outbox payload", e);
        }
    }
}
