# 0001 — Spring JDBC (`JdbcClient`) instead of JPA

- Status: accepted
- Date: 2026-11-04

## Context

The service reads and writes a small, well-understood schema: customers, products, orders,
order items, outbox events. The interesting query is "orders for a customer with their line
items, filtered by status, paged". That is one join and a count.

Both Spring Data JPA and Spring JDBC can do this. The choice affects how the SQL is
reviewed, how the paging behaves, and how the code lines up with the migration lab.

## Decision

Use `JdbcClient` with hand-written SQL. Repositories are interfaces (`repository/`) with
JDBC implementations (`repository/jdbc/`), so services are testable with fakes and the SQL is
visible in review.

## Why

- **The SQL is the design.** Paging with `limit`/`offset` and a filtered count is obvious
  in SQL and ambiguous in JPQL. `ORDER BY created_at DESC, id` is a deliberate, stable sort;
  with JPA it would be a `Pageable` whose tie-breaking is easy to lose.
- **No lazy-loading trap.** Order line items are loaded with one `IN` query for the whole
  page, not N+1. JPA would need an entity graph or a fetch join to match, and the failure
  mode (silently slow) is invisible in tests.
- **Postgres specifics stay usable.** The partial unique index that makes idempotency safe,
  the generated `line_total_cents` column, and `jsonb` outbox payloads are all natural in SQL
  and awkward through an ORM's abstraction.
- **It pairs with the migration lab.** That repo changes this schema with expand/contract
  steps. Keeping the application's SQL explicit means a reviewer can see whether a query is
  still valid during the expand phase.
- **Fewer moving parts.** No entity manager, no dirty checking, no second-level cache
  questions, no lazy-initialisation exceptions at the edges.

## Consequences

- Mapping rows to records is manual. It is repetitive, but it is also the place where a
  column rename actually shows up — which is the point.
- Bulk operations need explicit batching; there is no automatic flush ordering.
- If the domain grows entities with deep object graphs and heavy write-side invariants, JPA
  becomes the better tool. This schema is deliberately not that.
- Integration tests matter more, because there is no ORM to validate SQL at startup. That is
  why the Testcontainers suite exists and why CI runs it on every push.

## Alternatives considered

- **Spring Data JPA.** Familiar, and the right default for CRUD-heavy domains with complex
  entity relationships. Rejected here for N+1 risk and for hiding exactly the SQL this repo
  wants reviewed.
- **jOOQ.** Excellent for type-safe SQL, and a reasonable future upgrade. Rejected for now
  because it adds a code-generation step for five tables.
- **Plain `JdbcTemplate`.** Works, but `JdbcClient` is the modern, fluent surface over it.
