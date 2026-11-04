package io.slayyyyer.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end tests against a real Postgres and the real embedded server, so Flyway
 * migrations, the generated column, the partial unique index, problem details and the
 * GraphQL wiring are all exercised for real.
 *
 * <p>Driven over HTTP with the JDK client rather than MockMvc: the same bytes a client
 * would send, and no dependency on Boot's test-web autoconfiguration.
 *
 * <p>Requires Docker; runs under {@code ./mvnw verify -Pit}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrdersApiIT {

    private static final String ADA = "11111111-1111-4111-8111-111111111111";
    private static final String KEYBOARD = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String DESK_MAT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orders")
                    .withUsername("orders")
                    .withPassword("orders");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired private JdbcClient jdbc;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("creating an order prices it from the database and writes an outbox row")
    void createsOrderAndWritesOutbox() throws Exception {
        String body = """
                {"customerId":"%s","items":[
                  {"productId":"%s","quantity":2},
                  {"productId":"%s","quantity":1}
                ]}
                """.formatted(ADA, KEYBOARD, DESK_MAT);

        HttpResponse<String> response = post("/api/orders", body, "it-create-0001");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();

        JsonNode order = mapper.readTree(response.body());
        assertThat(order.get("totalCents").asLong()).isEqualTo(2L * 12499 + 3900);
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("items")).hasSize(2);

        Long outboxRows = jdbc.sql("select count(*) from outbox_events where event_type = 'order.created'")
                .query(Long.class)
                .single();
        assertThat(outboxRows).isNotNull().isPositive();
    }

    @Test
    @DisplayName("a replayed idempotency key returns 200 with the original order id")
    void replaysIdempotencyKey() throws Exception {
        String body = """
                {"customerId":"%s","items":[{"productId":"%s","quantity":1}]}
                """.formatted(ADA, KEYBOARD);

        HttpResponse<String> first = post("/api/orders", body, "it-replay-0001");
        assertThat(first.statusCode()).isEqualTo(201);
        String firstId = mapper.readTree(first.body()).get("id").asText();

        HttpResponse<String> replay = post("/api/orders", body, "it-replay-0001");

        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        assertThat(mapper.readTree(replay.body()).get("id").asText()).isEqualTo(firstId);

        Long ordersWithKey = jdbc.sql("select count(*) from orders where idempotency_key = 'it-replay-0001'")
                .query(Long.class)
                .single();
        assertThat(ordersWithKey).isEqualTo(1L);
    }

    @Test
    @DisplayName("GraphQL query joins line items and pages in SQL")
    void graphQlOrdersForCustomer() throws Exception {
        String query = """
                {"query":"query($id: ID!) { orders(customerId: $id, size: 5) { totalCount hasNextPage items { id status totalCents items { sku quantity lineTotalCents } } } }",
                 "variables":{"id":"%s"}}
                """.formatted(ADA);

        HttpResponse<String> response = post("/graphql", query, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode payload = mapper.readTree(response.body());
        assertThat(payload.has("errors")).isFalse();
        assertThat(payload.at("/data/orders/totalCount").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(payload.at("/data/orders/items/0/items/0/sku").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("validation failures return problem details with a field map")
    void validationFailureShape() throws Exception {
        String body = """
                {"customerId":"%s","items":[]}
                """.formatted(ADA);

        HttpResponse<String> response = post("/api/orders", body, "it-invalid-0001");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode problem = mapper.readTree(response.body());
        assertThat(problem.get("type").asText())
                .isEqualTo("https://slayyyyer.github.io/orders-service/problems/validation-failed");
        assertThat(problem.at("/errors/items").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("unknown customer is a 404 problem detail, not a stack trace")
    void unknownCustomerIs404() throws Exception {
        HttpResponse<String> response = get("/api/customers/99999999-9999-4999-8999-999999999999");

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode problem = mapper.readTree(response.body());
        assertThat(problem.get("type").asText())
                .isEqualTo("https://slayyyyer.github.io/orders-service/problems/not-found");
        assertThat(problem.get("resource").asText()).isEqualTo("customer");
        assertThat(response.body()).doesNotContain("Exception");
    }

    @Test
    @DisplayName("order listing filters by status and pages in SQL")
    void filtersOrdersByStatus() throws Exception {
        HttpResponse<String> response = get("/api/orders?customerId=" + ADA + "&status=PAID&size=10");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = mapper.readTree(response.body());
        assertThat(page.at("/items/0/status").asText()).isEqualTo("PAID");
        assertThat(page.get("totalCount").asLong()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("an order cannot skip PENDING -> PAID")
    void rejectsIllegalStatusTransition() throws Exception {
        String body = """
                {"customerId":"%s","items":[{"productId":"%s","quantity":1}]}
                """.formatted(ADA, KEYBOARD);

        HttpResponse<String> created = post("/api/orders", body, "it-transition-0001");
        assertThat(created.statusCode()).as("creating the order for the transition test returned: %s", created.body()).isEqualTo(201);
        String id = mapper.readTree(created.body()).get("id").asText();

        HttpResponse<String> response = post("/api/orders/" + id + "/status?status=SHIPPED", "", null);

        assertThat(response.statusCode()).as("illegal transition returned: %s", response.body()).isEqualTo(409);
        assertThat(mapper.readTree(response.body()).path("title").asText())
                .contains("conflicts");
    }

    private HttpResponse<String> post(String path, String body, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
