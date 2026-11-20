# Java Microservices App

Two Spring Boot services sharing one Postgres database (`postgres` in docker-compose, database `shop`).
There is ONE database connection for the whole system: both services connect through PgBouncer
(`pgbouncer/`), which holds that single connection and lends it to one transaction at a time.
Data lasts until `docker compose down`.

| Service           | Port | API             | UI                       |
|-------------------|------|-----------------|--------------------------|
| inventory-service | 8081 | `/api/items`, `/api/stock`, `/api/supplier-feed`, `/api/reconciliation` | http://<host>:8081/      |
| order-service     | 8082 | `/api/orders`, `/api/checkout` | (served by inventory UI) |

Each service seeds 10 sample records when its tables are empty.

## Features
- **Checkout** (order-service): a basket of inventory items for a customer. Reserves the stock in
  inventory-service, prices the basket (volume discount, coupon, tax), records one order per line, and
  releases the stock again if any step fails.
- **Supplier restock feed** (inventory-service): paste a supplier's `name;quantity;unitPrice` feed to add
  delivered units and update prices; rejected rows go to a plain-text reject report on stdout.
- **Packing slips** (order-service): every confirmed checkout prints a slip for the warehouse dock on the
  console, with each item's bin; **Reprint slip** prints it again.
- **Stock reconciliation** (inventory-service): checks every item's shelf count against its stock ledger;
  **Run** / **Cancel** on the dashboard.

See LOG_SCENARIOS.md for the log lines these produce.

## Run with Docker
```bash
docker compose up --build
```

## Run locally (JDK 17+ and Maven required)
Needs the database reachable as in `SPRING_DATASOURCE_URL` (default
`jdbc:postgresql://pgbouncer:6432/shop?prepareThreshold=0`, user/password `shop`). The compose services
publish no host port for it, so for a local run point it at your own Postgres, e.g.
`SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shop`.
Start BOTH services, in two terminals:
```bash
cd inventory-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
```
Then open http://localhost:8081/ (or http://<server-ip>:8081/ from another machine;
ports 8081 and 8082 must both be reachable).

## Quick API check
```bash
curl http://localhost:8081/api/items
curl http://localhost:8082/api/orders
curl -X POST http://localhost:8081/api/items -H "Content-Type: application/json" -d '{"name":"Desk Lamp","quantity":5}'
```
If an action fails in the UI, a red banner at the top now shows the exact URL and error.
