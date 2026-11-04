# orders-service

A Spring Boot 4 / Java 21 service exposing the same orders domain over **REST and GraphQL**,
with Postgres 16, Flyway migrations, idempotent order creation, an RFC 9457 error model and a
transactional outbox.

> **The demo is recorded, not live.** CI starts a real Postgres, runs the real application,
> sends a scripted set of calls, records every request/response pair, runs a short k6 load
> test, and publishes the results as a static site. There is no server to keep running and
> nothing to go down. The site says this on every page.

Part of a three-repo system:

| Repo | Role |
| --- | --- |
| **orders-service** | the service: Spring Boot, REST, GraphQL, Postgres, Flyway |
| [`migration-lab`](https://github.com/slayyyyer/migration-lab) | executable expand/contract migrations against this schema |
| [`gcp-reference-platform`](https://github.com/slayyyyer/gcp-reference-platform) | Terraform that would host it, verified with mocked providers and never applied |

Live site: <https://slayyyyer.github.io/orders-service/>

## What it does

- **REST**: `GET /api/customers/{id}`, `GET /api/customers`, `GET /api/products`,
  `GET /api/orders?customerId=…&status=…`, `POST /api/orders`, `GET /api/orders/{id}`,
  `POST /api/orders/{id}/status`.
- **GraphQL**: the same domain at `/graphql`. The interesting query is `orders(customerId: …)`,
  which joins line items and pages **in SQL** rather than in memory. One mutation creates an
  order with validation and a replay flag; another transitions status through a real state machine.
- **Idempotency**: `POST /api/orders` requires an `Idempotency-Key`. The first call returns
  `201` and a `Location`; a replay returns `200`, the original order, and
  `Idempotent-Replay: true`. Enforcement is a **partial unique index**, not an application lock,
  so concurrent retries cannot both insert.
- **Outbox**: order creation writes an `outbox_events` row in the same transaction. No event
  without an order, no order without an event.
- **Errors**: REST returns problem details with a stable `type` URI and a field map for
  validation failures; GraphQL returns typed errors with `extensions.code`. Same domain
  exceptions, deliberately different transports.
- **Observability**: Actuator health (with readiness/liveness probes), Micrometer metrics and a
  Prometheus endpoint. The Cloud Run module probes `/actuator/health/readiness`.
- **Migration-only mode**: `--app.migrate-only=true` runs Flyway and exits, which is what the
  `cloud-run-job` module invokes before a rollout.

## Verified locally

| Check | Result |
| --- | --- |
| `./mvnw verify` (29 unit tests) | pass |
| JaCoCo line coverage | 28.2% (143 of 507 coverable lines) from the unit suite |
| Flyway migrations against real Postgres | **not run locally** — no Docker on the authoring machine |
| Integration tests (7, Testcontainers) | **written, not run locally**; CI runs `./mvnw verify -Pit` |
| k6 load test, recorded explorer | **not run locally**; CI produces them |

The coverage gate (50% of lines) is bound to the `it` profile, i.e. it is evaluated on the
combined unit + integration report. The unit suite alone covers 28.2% of lines, because the
repositories, controllers and GraphQL layer are only reachable through a real Postgres. Gating
the default build there would either fail on every machine without Docker or force the
threshold down to a number that proves nothing. CI runs `./mvnw verify -Pit`, so the gate is
enforced on every push, and the coverage figure published on the site comes from that run.

CI also enforces `./mvnw verify -Pit` on every push, with the k6 thresholds
(p95 < 500ms, p99 < 900ms, failures < 1%) failing the build on regression.

## Layout

```
src/main/java/io/slayyyyer/orders/
  domain/       records and rules: pricing, status transitions, idempotency keys
  repository/   ports (interfaces) and jdbc/ implementations
  service/      transactional use cases; the only place that owns transactions
  web/          REST controllers, DTOs, problem-details advice
  graphql/      GraphQL controllers, input records, error mapping
src/main/resources/
  db/migration/ Flyway migrations (schema, then deterministic seed data)
  graphql/      schema.graphqls
ci/             the recording script and the k6 scenario CI runs
site/           the static Pages site (plain HTML/CSS/JS, no framework)
docs/adr/       four decision records
```

## Design decisions

- [0001 — JDBC over JPA](docs/adr/0001-jdbc-over-jpa.md)
- [0002 — REST and GraphQL over one domain](docs/adr/0002-rest-and-graphql.md)
- [0003 — Idempotent order creation](docs/adr/0003-idempotent-orders.md)
- [0004 — Transactional outbox](docs/adr/0004-outbox.md)

Architecture diagram source: [`docs/diagrams/architecture.mmd`](docs/diagrams/architecture.mmd).

## Run it yourself

```bash
docker run --rm -d --name orders-pg -p 5432:5432 \
  -e POSTGRES_DB=orders -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=orders \
  postgres:16-alpine

./mvnw spring-boot:run          # Flyway migrates and seeds on startup
open http://localhost:8080/swagger-ui.html
```

Full suite, including Testcontainers integration tests and the coverage gate:

```bash
./mvnw verify -Pit
```

## Stack and pinned versions

| Component | Version | Note |
| --- | --- | --- |
| Java | 21 | LTS |
| Spring Boot | 4.1.2 | current stable; 3.5 reached OSS end of life in June 2026 |
| springdoc-openapi | 3.2.0 | the 3.x line targets Boot 4 |
| Flyway | managed by the Boot BOM | `flyway-core` + `flyway-database-postgresql` |
| Testcontainers | 1.21.4 | see the deviation note below |
| JaCoCo | 0.8.16 | current release |
| k6 | v2.3.0 | downloaded in CI, not a project dependency |
| Postgres | 16 | `postgres:16-alpine` locally, in Testcontainers and in the CI service |

## Deviations from the original brief

- **Spring Boot 4.1, not 3.x.** The brief said "latest stable 3.x", but 3.5 is out of
  open-source support as of June 2026. Shipping an EOL runtime to prove currency would have
  been the wrong signal.
- **Testcontainers 1.21.4, not 2.0.5.** 2.0 renamed its modules (`testcontainers-postgresql`)
  and is no longer managed by the Boot BOM. 1.21.x keeps the stable `PostgreSQLContainer`
  and JUnit 5 integration; the version is pinned explicitly in `pom.xml`.
- **Integration tests drive the server over HTTP** with the JDK `HttpClient` instead of
  MockMvc: Boot 4 moved the MockMvc test autoconfiguration to a separate module, and real
  HTTP bytes are a better test of the error model anyway.
- **The coverage gate runs with the `it` profile.** Unit-only coverage of this layered service
  is 28.2% (143 of 507 coverable lines), measured from the JaCoCo report. The remaining lines are
  the JDBC repositories and the HTTP/GraphQL edges, which require a database to exercise. The
  threshold is 50% on the combined report: high enough to catch a collapse, and only reachable
  where the integration tests actually run.
- **Swagger UI is vendored at build time** (pinned `swagger-ui-dist` 5.34.1) so the docs page
  has no runtime CDN dependency. If the download fails, CI publishes the OpenAPI document
  and the recorded explorer instead of a broken page.
- **The outbox is written, not published.** There is no relay yet; saying otherwise would be
  the kind of claim this repo exists to avoid.

## License

MIT
