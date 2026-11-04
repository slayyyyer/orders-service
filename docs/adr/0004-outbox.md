# 0004 — Transactional outbox, written but not published

- Status: accepted (relay explicitly deferred)
- Date: 2026-11-04

## Context

Downstream consumers will eventually care that an order was created or paid. The naive options
are both wrong:

- **Publish inside the request handling, after the commit.** If the publish fails, the order
  exists and the event is lost.
- **Publish before the commit.** If the transaction rolls back, the event describes an order
  that never existed.

## Decision

Write an `outbox_events` row in the same transaction as the order:

```sql
insert into outbox_events (aggregate_id, event_type, payload) ...
```

`payload` is `jsonb`; `published_at` is nullable and indexed for unpublished rows only. There
is **no relay in this repository yet**. The table is written, and a future worker would read
unpublished rows and publish them to Pub/Sub (the topic exists in the Terraform repo).

## Why

- **Atomicity for free.** The event commits or rolls back with the order, because it is the
  same transaction. No distributed transaction, no two-phase commit.
- **At-least-once is achievable; exactly-once is not.** The outbox makes the write durable and
  lets a relay retry publishing until it succeeds. Consumers must be idempotent, and saying so
  out loud is better than pretending otherwise.
- **The schema is the contract.** `aggregate_id`, `event_type`, `payload`, `created_at`,
  `published_at` is enough for a relay to work without changing the writing side.
- **Deferring the relay is honest.** A relay needs a poller, a publisher and a metric. Writing
  a half-working one to claim "event-driven" would be exactly the overclaim this portfolio is
  trying not to make.

## What is missing

- No relay, no published-at updates, no dead-letter handling for failed publishes.
- No schema/registry for `payload`; the shape is whatever the writer puts in it.
- No retention or partitioning. At high volume this table needs both.

## Consequences

- Extra write per order. Cheap, and it removes a whole class of "event lost on failure" bugs.
- The outbox grows. Without a relay or an archival job, that growth is unbounded — acceptable
  for a reference implementation, documented rather than hidden.
- Consumers of a future relay get at-least-once delivery, so they must deduplicate on
  `aggregate_id` plus `event_type` plus a version or timestamp.
