# FoodChain API Gateway

Central entry point for all FoodChain microservices. Built with **Spring Cloud Gateway** and registered in **Eureka** for client-side load balancing.

- **Port:** `8080`
- **Eureka name:** `api-gateway`
- **Swagger UI:** http://localhost:8080/swagger-ui.html

---

## Route Table

All requests enter at port `8080`. The gateway strips nothing — paths are forwarded as-is.

| Path Prefix | Downstream Service | Notes |
|---|---|---|
| `POST /api/orders/**` | `order-service` | HTTP only |
| `/api/menu/**` | `menu-service` | |
| `/api/branch/**` | `branch-service` | Kept for backward compatibility |
| `/api/branches/**` | `branch-service` | Canonical path |
| `/api/kitchen/**` | `kitchen-service` | |
| `/api/analytics/**` | `analytics-report-service` | |
| `/api/reports/**` | `analytics-report-service` | |
| `/api/manager/**` | `analytics-report-service` | Manager dashboard endpoints |
| `/api/admin/analytics/**` | `analytics-report-service` | Admin analytics endpoints |
| `/api/notifications/**` | `notifications-service` | |
| `/api/auth/**` | `user-service` | Login, register, refresh, forgot-password |
| `/api/users/**` | `user-service` | User profile / management |
| `/api/admin/users/**` | `user-service` | Admin user management |

### WebSocket Routes

| Path Prefix | Downstream Service | Protocol |
|---|---|---|
| `/ws-notifications/**` | `notifications-service` | STOMP over WebSocket |
| `/ws/kitchen/**` | `notifications-service` | Raw WebSocket (`lb:ws://`) |
| `/ws/orders/**` | `notifications-service` | Raw WebSocket (`lb:ws://`) |
| `/ws/manager/**` | `notifications-service` | Raw WebSocket (`lb:ws://`) |

WebSocket paths bypass JWT validation entirely (pass-through).

### OpenAPI Docs Proxy Routes

Each service exposes docs at `/api/v3/api-docs`. The gateway rewrites these to a stable public URL:

| Gateway URL | Proxied To |
|---|---|
| `/v3/api-docs/user-service` | `user-service:/api/v3/api-docs` |
| `/v3/api-docs/order-service` | `order-service:/api/v3/api-docs` |
| `/v3/api-docs/menu-service` | `menu-service:/api/v3/api-docs` |
| `/v3/api-docs/branch-service` | `branch-service:/api/v3/api-docs` |
| `/v3/api-docs/kitchen-service` | `kitchen-service:/api/v3/api-docs` |
| `/v3/api-docs/analytics-report-service` | `analytics-report-service:/api/v3/api-docs` |
| `/v3/api-docs/notifications-service` | `notifications-service:/api/v3/api-docs` |

---

## JWT Validation

Handled by `JwtAuthFilter` — a `GlobalFilter` with order `-1` (runs before all other filters).

### Public Paths (no token required)

Requests to any of these path prefixes skip JWT validation:

| Path |
|---|
| `/api/auth/login` |
| `/api/auth/register` |
| `/api/auth/refresh` |
| `/api/auth/forgot-password` |
| `/actuator/**` |
| `/swagger-ui/**` |
| `/v3/api-docs/**` |
| `/webjars/**` |
| `/swagger-resources/**` |

WebSocket paths (`/ws/**`, `/ws-notifications/**`) also bypass validation unconditionally.

### Protected Paths

All other paths require a valid `Authorization: Bearer <token>` header.

On validation failure the gateway returns `401 Unauthorized` immediately — the request never reaches a downstream service.

### Headers Forwarded to Downstream Services

After successful JWT validation the gateway **removes** the `Authorization` header and forwards these instead:

| Header | JWT Source | Notes |
|---|---|---|
| `X-User-Id` | `sub` (JWT subject) | User UUID |
| `X-User-Role` | `role` claim | Role string e.g. `ADMIN`, `MANAGER`, `STAFF` |
| `X-User-Email` | `email` claim | User's email address |
| `X-User-BranchId` | `branchId` claim | Only set when the claim is non-null |

Downstream services should read these headers instead of parsing the JWT themselves.

---

## CORS Configuration

Global CORS is controlled with `CORS_ALLOWED_ORIGINS`. Use a comma-separated list of exact frontend origins.

```yaml
globalcors:
  cors-configurations:
    "[/**]":
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}
      allowed-methods: "*"
      allowed-headers: "*"
```

---

## Swagger UI

Access the aggregated Swagger UI at:

```
http://localhost:8080/swagger-ui.html
```

Use the dropdown in the top-right corner to switch between services:

- User Service
- Order Service
- Menu Service
- Branch Service
- Kitchen Service
- Analytics & Report Service
- Notifications Service

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `your-256-bit-secret-key-change-this-in-production-min-32-chars` | HS256 signing key — must be at least 32 characters. **Change in production.** |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka server URL (set via `eureka.client.service-url.defaultZone`) |

The gateway imports additional config from the config server (`http://localhost:8888`) when available, controlled by:

```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

---

## Running Locally

1. Start Eureka server (port `8761`) and Config server (port `8888`).
2. Start downstream microservices — they register themselves in Eureka.
3. Start the gateway:

```bash
./mvnw spring-boot:run
```

Or with Docker Compose — see `/Users/ubaydah/Desktop/foodchain-deployment`.
