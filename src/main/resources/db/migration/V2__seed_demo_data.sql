-- Deterministic seed data so the recorded demo produces the same output every run.
-- Fixed UUIDs, fixed prices, fixed timestamps: no randomness anywhere.

insert into customers (id, email, full_name, created_at) values
    ('11111111-1111-4111-8111-111111111111', 'ada@example.com',   'Ada Lovelace',  '2026-01-05T09:00:00Z'),
    ('22222222-2222-4222-8222-222222222222', 'grace@example.com', 'Grace Hopper',  '2026-01-06T09:00:00Z')
on conflict do nothing;

insert into products (id, sku, name, unit_price_cents, created_at) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'KEY-MECH-01', 'Mechanical keyboard', 12499, '2026-01-02T09:00:00Z'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'DESK-MAT-02', 'Desk mat',              3900, '2026-01-02T09:00:00Z'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'CABLE-USBC-03', 'USB-C cable, 2 m',     1499, '2026-01-02T09:00:00Z')
on conflict do nothing;

insert into orders (id, customer_id, status, currency, total_cents, idempotency_key, created_at, updated_at) values
    ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
     '11111111-1111-4111-8111-111111111111',
     'PAID', 'USD', 16399, 'seed-order-0001',
     '2026-02-01T10:00:00Z', '2026-02-01T10:00:00Z')
on conflict do nothing;

insert into order_items (id, order_id, product_id, quantity, unit_price_cents) values
    ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
     'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
     'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 1, 12499),
    ('ffffffff-ffff-4fff-8fff-ffffffffffff',
     'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
     'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 1, 3900)
on conflict do nothing;
