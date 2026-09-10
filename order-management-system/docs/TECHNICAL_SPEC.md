# Technical Specification — Relational Order Management & Transaction Processing System

## 1. Purpose

A multi-tier back-end service that manages authenticated transactional
records (orders, order lines, payment-ledger transactions) and applies
real-time inventory adjustments as orders are placed, while guaranteeing
ACID consistency under concurrent read/write load.

## 2. Architecture

```
┌──────────────┐     ┌──────────────────────────────────────────────┐     ┌───────────┐
│   Main /     │────▶│                 Service Layer                 │────▶│  MySQL 8  │
│  future REST │     │  OrderService: orchestrates one ACID unit of  │     │ (InnoDB)  │
│  controller  │     │  work per order across 4 DAOs on one JDBC     │     │           │
└──────────────┘     │  Connection (auto-commit off, REPEATABLE_READ)│     └───────────┘
                      └──────────────────────────────────────────────┘
                                │        │         │          │
                          ProductDao CustomerDao OrderDao TransactionDao
                          (impl: PreparedStatement-only, HikariCP-pooled)

Cross-cutting: GlobalExceptionHandler (exception/), Validator + InputSanitizer
(util/), logback (diagnostic logs with correlation IDs).
```

Layers, bottom to top:

1. **model** — plain entity/enum classes (`Product`, `Customer`, `Order`,
   `OrderItem`, `Transaction`). No behavior, no JDBC.
2. **dao / dao.impl** — one interface + implementation per table family.
   Every SQL statement is a `PreparedStatement`; no string concatenation
   of caller input into SQL anywhere in the codebase. Multi-step DAO calls
   that must commit together accept the caller's `Connection` so the
   service layer controls the transaction boundary.
3. **service** — `OrderService` is the only class that opens a transaction.
   It validates input, locks and checks inventory, writes the order graph,
   and records the ledger entry, all inside one commit/rollback block.
4. **exception / util** — shared contracts: a typed exception hierarchy,
   a single `GlobalExceptionHandler.handle()` funnel, and reusable
   validation/sanitization helpers used by every public service method.
5. **config** — `DatabaseConfig` builds a HikariCP-pooled `DataSource` from
   environment variables (Docker) falling back to
   `application.properties` (local dev).

## 3. Data Model (ERD summary)

| Table | Key columns | Notes |
|---|---|---|
| `products` | `id`, `sku` (unique), `quantity_on_hand`, `unit_price` | `CHECK (quantity_on_hand >= 0)` is the last line of defense against over-selling |
| `customers` | `id`, `email` (unique) | |
| `orders` | `id`, `customer_id` → `customers.id`, `status` | status ∈ {PENDING, CONFIRMED, CANCELLED, COMPLETED} |
| `order_items` | `id`, `order_id` → `orders.id` (CASCADE), `product_id` → `products.id`, `unit_price` (snapshotted) | price is copied at purchase time so later catalog price changes don't retroactively alter past orders |
| `transactions` | `id`, `order_id` → `orders.id` (CASCADE), `reference_code` (unique), `status` | financial ledger, 1 row per settlement attempt |
| `inventory_audit_log` | `id`, `product_id`, `change_qty`, `reason` | append-only trail for stock adjustments (schema provided; write path left as an extension point) |

Full DDL: [`sql/schema.sql`](../sql/schema.sql).

## 4. ACID / Concurrency Design

**Atomicity & Consistency.** `OrderService.placeOrder` performs the order
insert, N order-item inserts, N inventory decrements, and the ledger insert
on a single `Connection` with `autoCommit(false)`. Any exception —
validation failure, insufficient stock, or a `SQLException` — triggers
`conn.rollback()` before the exception propagates, so a failed order never
leaves partial rows behind.

**Isolation.** The connection is raised to `TRANSACTION_REPEATABLE_READ`
(MySQL InnoDB's default and the level at which its gap-locking prevents
phantom reads within the transaction). Each product row touched by an
order is read with `SELECT ... FOR UPDATE`
(`ProductDao.findByIdForUpdate`), taking an exclusive row lock for the
remainder of the transaction. Two concurrent orders against the same SKU
therefore serialize on that row instead of both reading the same
pre-decrement quantity and over-selling stock.

**Durability.** Delegated to InnoDB (`innodb_flush_log_at_trx_commit=1` by
default in the official MySQL image used by `docker-compose.yml`).

**Contract tests.** `OrderServiceTest` verifies both the commit path
(inventory decremented by exactly the ordered amount, ledger entry
recorded) and the rollback path (an `InsufficientInventoryException`
results in zero inventory writes, zero ledger writes, and a `rollback()`
call — see `placeOrder_insufficientInventory_rollsBackAndThrowsWithoutRecordingTransaction`).

## 5. Exception Handling Strategy

All checked `SQLException`s are caught at the DAO boundary and rethrown as
the unchecked `DataAccessException`, so nothing above the DAO layer
handles JDBC's checked-exception contract directly. Business-rule failures
use dedicated types (`ValidationException`, `InsufficientInventoryException`,
`OrderProcessingException`), all rooted in `OmsException`, which carries a
stable `errorCode` string. `GlobalExceptionHandler.handle(Throwable)` is
the single place that decides log level, generates a correlation ID
(propagated via SLF4J's `MDC`), and produces a client-safe
`HandledError(correlationId, errorCode, message)` — internal detail (stack
traces, SQL fragments) is written only to the diagnostic log, never
returned to a caller.

## 6. Input Sanitization

- **Structural:** every SQL statement in `dao.impl` uses
  `PreparedStatement` with bound parameters — there is no path in this
  codebase where user-supplied data is concatenated into SQL text.
- **Semantic:** `util.Validator` enforces non-blank / positive / non-null
  / email-format contracts on every public `OrderService` argument before
  any database call is made.
- **Defensive cleanup:** `util.InputSanitizer` strips ASCII control
  characters and normalizes whitespace on free-text fields before they are
  persisted or logged.

## 7. Logging & Diagnostics

Logback writes to console and to a size- and time-rotated file
(`logs/oms-*.log.gz`, 14-day retention). Every log line includes the
active correlation ID via `%X{correlationId}`, and `GlobalExceptionHandler`
guarantees that ID is set for the duration of any failure handling, so a
single ID can be grep'd across the log file to reconstruct one failed
request end-to-end.

## 8. Testing Strategy ("functional contract testing")

Tests assert the **documented contract** of each public method — its
preconditions and postconditions — rather than its internal call sequence:

- `OrderServiceTest` — happy path (inventory decremented by exactly the
  ordered quantity, total computed correctly, transaction committed);
  insufficient-inventory path (no partial writes, rollback invoked);
  invalid-customer / empty-lines / non-positive-quantity paths (rejected
  before any write).
- `ValidatorTest` — boundary behavior of every validation rule (blank,
  zero, negative, malformed email) plus the sanitizer's control-character
  stripping.

DAOs are mocked in service tests so the suite runs without a live
database (`mvn test`); the full stack is exercised manually via
`docker-compose up` + `Main`, matching how the service will actually run
in production.

## 9. Deployment

See [`README.md`](../README.md) for full instructions. Summary: a
multi-stage `Dockerfile` (Maven build stage → slim JRE runtime stage) plus
a `docker-compose.yml` that provisions MySQL 8.4, auto-applies
`sql/schema.sql` and `sql/seed.sql` on first boot via
`docker-entrypoint-initdb.d`, waits for a MySQL healthcheck, then starts
the app container with connection parameters injected via environment
variables.

## 10. Non-Functional Notes / Extension Points

- Connection pool size (`DB_POOL_SIZE`) is externalized so it can be tuned
  to the deployment's expected concurrent load without a code change.
- `inventory_audit_log` schema is in place for a full stock-adjustment
  audit trail; wiring `ProductDao.adjustQuantity` to also insert an audit
  row is a natural next increment.
- `Main` is intentionally thin — it exists to demonstrate the wiring; a
  REST layer (e.g. a servlet or Spring MVC controller) would sit in front
  of the same unchanged `OrderService` in a production deployment.
