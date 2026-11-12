# Java Microservices App

Two Spring Boot services with in-memory H2 databases (data resets on restart):

| Service           | Port | API             | UI                       |
|-------------------|------|-----------------|--------------------------|
| inventory-service | 8081 | `/api/items`, `/api/stock`, `/api/supplier-feed` | http://<host>:8081/      |
| order-service     | 8082 | `/api/orders`, `/api/checkout` | (served by inventory UI) |

Each service seeds 10 sample records on startup.

## Features
- **Checkout** (order-service): a basket of inventory items for a customer. Reserves the stock in
  inventory-service, prices the basket (volume discount, coupon, tax), records one order per line, and
  releases the stock again if any step fails.
- **Supplier restock feed** (inventory-service): paste a supplier's `name;quantity;unitPrice` feed to add
  delivered units and update prices; rejected rows go to a plain-text reject report on stdout.

See LOG_SCENARIOS.md for the log lines these produce.

## Run with Docker
```bash
docker compose up --build
```

## Run locally (JDK 17+ and Maven required)
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
