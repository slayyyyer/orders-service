# 0002 — REST and GraphQL over one domain

- Status: accepted
- Date: 2026-11-04

## Context

The brief asks for both REST endpoints and a GraphQL schema over the same domain, including a
non-trivial query and a mutation with validation and a clear error model. Two transports over
one domain is a common real-world situation (a public API plus a product-facing graph), and a
common way to end up with two divergent implementations.

## Decision

Both transports call the same services. GraphQL lives at `/graphql`; REST lives under `/api`.
Neither transport contains business rules.

- Services own validation, pricing, transactions and idempotency.
- REST maps domain exceptions to RFC 9457 problem details.
- GraphQL maps the same exceptions to typed GraphQL errors with `extensions.code`.

## Why

- **One place for rules.** A price computed in two places is a price that will eventually
  disagree. Both `OrderService.create` and `OrderService.transition` are transport-agnostic.
- **The contracts should differ, the behaviour should not.** REST clients want stable status
  codes and machine-readable problem details; GraphQL clients want partial data and typed
  errors. Pretending one error shape fits both is how GraphQL APIs end up returning 200 with
  a stringly-typed `errors` array that clients cannot branch on.
- **The non-trivial query earns its place.** `orders(customerId, status, page, size)` returns
  a connection with `totalCount` and `hasNextPage`. It joins line items in one query and pages
  in SQL, so it demonstrates something REST-shaped code often gets wrong (paging in memory).
- **Validation is shared.** Bean Validation guards the REST body; the GraphQL input record is
  mapped into the same command, so the same rules apply. `IdempotencyKeys` validates the key
  in the service, not in either controller.

## Consequences

- Two sets of DTOs to maintain. The GraphQL schema exposes domain records directly because
  their shape is already the contract; REST has explicit DTOs because a wire format should not
  be a domain record by accident.
- Two error surfaces to document and test. The integration suite asserts both shapes.
- GraphQL adds a schema to keep in sync with the domain. A mismatch fails at startup, which is
  the earliest useful moment.
- No GraphQL subscriptions or batching. If a consumer needs either, that is a new decision,
  not a free addition.

## Alternatives considered

- **GraphQL only.** Fewer surfaces, but REST is what most infrastructure (health checks,
  webhooks, simple integrations) expects, and the brief asks for both.
- **REST only.** Would drop a genuinely different query shape and the schema-as-contract.
- **Two services.** Correct at large scale, absurd for one bounded context.
