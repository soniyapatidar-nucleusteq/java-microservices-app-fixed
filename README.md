# Java Microservices App

Two Spring Boot services with in-memory H2 databases (data resets on restart):

| Service           | Port | API             | UI                       |
|-------------------|------|-----------------|--------------------------|
| inventory-service | 8081 | `/api/items`    | http://<host>:8081/      |
| order-service     | 8082 | `/api/orders`   | (served by inventory UI) |

Each service seeds 10 sample records on startup.

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
