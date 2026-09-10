# Relational Order Management & Transaction Processing System

A multi-tier back-end service for managing authenticated transactional
records and real-time inventory adjustments, built on **Java, JDBC, and
MySQL**, deployed with **Docker**.

Order placement is handled as a single ACID unit of work: inventory rows
are row-locked and checked, order/order-item/ledger rows are written, and
the whole operation commits or rolls back together — so concurrent orders
against the same product can never oversell stock.

## Tech Stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Persistence | MySQL 8.4, raw JDBC (`PreparedStatement` only — no ORM) |
| Connection pooling | HikariCP |
| Logging | SLF4J + Logback (rotating file + console, correlation IDs) |
| Testing | JUnit 5, Mockito |
| Build | Maven (shaded runnable jar) |
| Deployment | Docker, Docker Compose |

## Architecture

```
Main (CLI demo)
      │
      ▼
OrderService  ──▶ validates input, opens ONE JDBC transaction per order
      │             (autoCommit=false, REPEATABLE_READ)
      ▼
ProductDao / CustomerDao / OrderDao / TransactionDao
      │             (PreparedStatement-only; SELECT ... FOR UPDATE on
      ▼              inventory rows to serialize concurrent orders)
   MySQL (InnoDB)
```

See [`docs/TECHNICAL_SPEC.md`](docs/TECHNICAL_SPEC.md) for the full design
write-up: data model, ACID/concurrency guarantees, exception-handling
strategy, and testing approach.

## Project Structure

```
order-management-system/
├── src/main/java/com/example/oms/
│   ├── Main.java                 # demo entry point / usage example
│   ├── config/DatabaseConfig.java
│   ├── model/                    # Product, Customer, Order, OrderItem, Transaction, enums
│   ├── dao/                      # persistence contracts
│   ├── dao/impl/                 # JDBC implementations (PreparedStatement only)
│   ├── service/OrderService.java # ACID order-placement orchestration
│   ├── service/dto/              # OrderLineRequest, OrderResult
│   ├── exception/                # typed exception hierarchy + GlobalExceptionHandler
│   └── util/                     # Validator, InputSanitizer
├── src/main/resources/
│   ├── application.properties    # local-dev DB defaults
│   └── logback.xml               # console + rotating file logging
├── src/test/java/com/example/oms/
│   ├── service/OrderServiceTest.java   # functional contract tests
│   └── util/ValidatorTest.java
├── sql/schema.sql                # DDL
├── sql/seed.sql                  # sample data
├── docs/TECHNICAL_SPEC.md
├── Dockerfile                    # multi-stage: Maven build -> slim JRE runtime
├── docker-compose.yml            # app + MySQL, auto-applies schema/seed on first boot
└── .github/workflows/ci.yml      # build + test on push/PR
```

## Running It

### Option 1 — Docker (recommended)

```bash
docker compose up --build
```

This starts MySQL 8.4 (auto-applying `sql/schema.sql` and `sql/seed.sql`
on first boot), waits for it to pass its healthcheck, then starts the app
container, which places one demo order and logs the result.

### Option 2 — Local (Maven + a MySQL you already have running)

```bash
# 1. Create the schema and sample data
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/seed.sql

# 2. Point the app at your DB (or edit src/main/resources/application.properties)
export DB_HOST=localhost DB_USER=oms_user DB_PASSWORD=oms_password DB_NAME=oms_db

# 3. Build and run
mvn clean package
java -jar target/oms.jar
```

### Running the tests

```bash
mvn test
```

`OrderServiceTest` covers the transactional contract directly — inventory
decremented by exactly the ordered quantity and a `SUCCESS` ledger entry
on the happy path; zero partial writes and an explicit `rollback()` when
stock is insufficient. `ValidatorTest` covers the input-sanitization
boundary conditions.

## Environment Variables (Docker / production)

| Variable | Default (compose) | Purpose |
|---|---|---|
| `DB_HOST` | `mysql` | MySQL hostname |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `oms_db` | Schema name |
| `DB_USER` | `oms_user` | DB user |
| `DB_PASSWORD` | `oms_password` | DB password |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |

## License

MIT — see [LICENSE](LICENSE).
