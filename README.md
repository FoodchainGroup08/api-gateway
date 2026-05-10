# FoodChain API Gateway

Central entry point for all FoodChain microservices. Built with **Spring Cloud Gateway** and registered in **Eureka** for client-side load balancing.

- **Port:** `8080`
- **Eureka name:** `api-gateway`
- **Swagger UI:** http://localhost:8080/swagger-ui.html

Clients should call **this** host for HTTP APIs (not individual service ports), unless you are debugging a service directly.

---

## Route table

All traffic enters on port **8080**. Paths use the **`/api/v1/**`** prefix (see `application.yml` / config server `api-gateway.yml`).

| Path prefix | Downstream service |
|-------------|-------------------|
| `/api/v1/orders/**` | `order-service` |
| `/api/v1/payments/**` | `order-service` (e.g. Paystack webhook — **no JWT**; `POST .../paystack/webhook` is public) |
| `/api/v1/menu/**` | `menu-service` |
| `/api/v1/branch/**` | `branch-service` (legacy singular path, if configured) |
| `/api/v1/branches/**` | `branch-service` (canonical branches API) |
| `/api/v1/kitchen/**` | `kitchen-service` |
| `/api/v1/analytics/**` | `analytics-report-service` |
| `/api/v1/reports/**` | `analytics-report-service` |
| `/api/v1/manager/**` | `analytics-report-service` |
| `/api/v1/admin/analytics/**` | `analytics-report-service` |
| `/api/v1/notifications/**` | `notifications-service` |
| `/api/v1/auth/**` | `user-service` |
| `/api/v1/users/**` | `user-service` |
| `/api/v1/admin/users/**` | `user-service` |

### WebSocket routes

| Path prefix | Downstream | Protocol |
|-------------|------------|----------|
| `/ws-notifications/**` | `notifications-service` | STOMP / SockJS |
| `/ws/kitchen/**` | `notifications-service` | Raw WebSocket (`lb:ws://`) |
| `/ws/orders/**` | `notifications-service` | Raw WebSocket |
| `/ws/manager/**` | `notifications-service` | Raw WebSocket |

WebSocket paths **do not** require JWT in this gateway filter (pass-through).

### OpenAPI docs proxy

Each service exposes docs under its own context path. The gateway exposes a stable URL per service:

| Gateway URL | Proxied to |
|-------------|------------|
| `/v3/api-docs/user-service` | `user-service` → `/api/v3/api-docs` |
| `/v3/api-docs/order-service` | `order-service` → `/api/v3/api-docs` |
| `/v3/api-docs/menu-service` | `menu-service` → `/api/v3/api-docs` |
| `/v3/api-docs/branch-service` | `branch-service` → `/api/v3/api-docs` |
| `/v3/api-docs/kitchen-service` | `kitchen-service` → `/api/v3/api-docs` |
| `/v3/api-docs/analytics-report-service` | `analytics-report-service` → `/api/v3/api-docs` |
| `/v3/api-docs/notifications-service` | `notifications-service` → `/api/v3/api-docs` |

---

## JWT validation (`JwtAuthFilter`)

Order: `-1` (runs before route predicates). Validates HS256 JWT using `jwt.secret` (same secret as **user-service**).

### Anonymous requests (no `Authorization` header)

| Rule | Details |
|------|---------|
| **OPTIONS** | All paths (CORS preflight). |
| **Always public** | Prefixes: `/actuator`, `/swagger-ui`, `/v3/api-docs`, `/webjars`, `/swagger-resources`. |
| **Auth (unauthenticated)** | `POST`/`GET` etc. under `/api/v1/auth/**` **except** `GET /api/v1/auth/me` (me requires a token). |
| **GET only** | **Exactly** `GET /api/v1/branches` (paginated list), and **GET** `/api/v1/branches/nearby` (location search). |
| **POST (webhook)** | **Exactly** `POST /api/v1/payments/paystack/webhook` (Paystack server-to-server callback). |

Everything else requires:

```http
Authorization: Bearer <access-token>
```

Failed validation returns **401** with a **JSON** body: `status`, `error`, `message`, `path`, `timestamp`.

### After successful validation

The gateway **removes** `Authorization` and forwards identity to downstream services:

| Header | JWT claim | Notes |
|--------|-----------|--------|
| `X-User-Id` | `sub` | User UUID |
| `X-User-Role` | `role` | Enum-style role, e.g. `HEAD_OFFICE_ADMIN`, `CUSTOMER`, `KITCHEN_STAFF` |
| `X-User-Email` | `email` | |
| `X-User-BranchId` | `branchId` | Omitted if claim absent |

Downstream services that do not re-parse JWT rely on these headers (especially **`X-User-Role`** for admin checks). **user-service** can still authenticate when `Authorization: Bearer` is sent directly (bypassing the gateway).

---

## CORS

Permissive for development (`allowed-origins: *`). Restrict in production.

---

## Swagger UI

http://localhost:8080/swagger-ui.html — use the dropdown to pick each service’s OpenAPI definition.

---

## Environment variables

| Variable | Purpose |
|----------|---------|
| `JWT_SECRET` | HS256 key (min 32 chars). Must match **user-service**. |
| Eureka | `eureka.client.service-url.defaultZone` — default `http://localhost:8761/eureka/` |

Optional config server:

```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

---

## Running locally

1. Start **Eureka** (`8761`) and optionally **Config server** (`8888`).
2. Start business microservices; they register in Eureka.
3. Run the gateway: `./mvnw spring-boot:run`

Docker-based orchestration: see **foodchain-deployment**.
