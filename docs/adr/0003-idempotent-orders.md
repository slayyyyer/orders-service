# 0003 — Idempotent order creation via a partial unique index

- Status: accepted
- Date: 2026-11-04

## Context

Clients retry. A timeout does not tell the caller whether the order was created, so retrying
is the only safe behaviour — unless retrying creates a second order and charges the customer
twice.

## Decision

`POST /api/orders` requires an `Idempotency-Key` header. The key is stored on the order, and a
**partial unique index** enforces it:

```sql
create unique index orders_idempotency_key_key
    on orders (idempotency_key)
    where idempotency_key is not null;
```

Flow:

1. Validate the key (`8..128` chars, `[A-Za-z0-9._:-]`).
2. Look up an existing order by key. If found, return it with `replayed = true` (`200`,
   `Idempotent-Replay: true`).
3. Otherwise create the order inside a transaction.
4. If the insert loses a race and raises a duplicate-key error, re-read by key and return the
   winner's order.

## Why

- **The database is the arbiter.** A `SELECT`-then-`INSERT` in application code has a race
  window no amount of care removes. A unique index does not.
- **The index is partial.** Orders created without a key (seed data, internal tooling) must
  not collide with each other. `where idempotency_key is not null` keeps NULLs out of the
  uniqueness contract instead of relying on Postgres NULL semantics.
- **Replay is a 200, not a 201.** The caller did not create anything on the second call, and a
  client that treats 201 as "new order" should see the difference.
- **The key is bounded.** An unbounded key is a free way to write megabytes into an index.

## What this does not do

- It does not compare request bodies. A client that reuses a key with a *different* body gets
  the original order, not a `409`. That is intentional: the key is the contract, and body
  comparison would need a canonical hash of the request, which is a different feature.
- It does not expire keys. Retention is a data-lifecycle decision, and this schema has no
  retention job.
- It does not make `POST /api/orders/{id}/status` idempotent. Status transitions are guarded by
  the state machine instead: replaying `PAID` on a `PAID` order is a conflict, not a no-op.

## Consequences

- Every client must generate a key. That is the correct price for safe retries.
- The race path relies on Postgres raising a unique violation, which the service catches as
  `DuplicateKeyException`. The integration test asserts exactly one order exists after a replay.
- If the key is reused across different customers, the original order is returned to the wrong
  caller. Keys are scoped globally here; scoping them per customer is a reasonable future change
  and would need a composite index instead.
