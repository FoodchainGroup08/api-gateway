package com.microservices.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Always public regardless of HTTP method. */
    private static final List<String> ALWAYS_PUBLIC = List.of(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",
            "/swagger-resources"
    );

    /**
     * Only these GET paths are anonymous — everything else requires {@code Authorization: Bearer}.
     * Branch list and nearby search stay public; branch-by-id and all menu/notifications GETs require auth.
     */
    private static final Pattern PUBLIC_BRANCH_LIST = Pattern.compile("^/api/v1/branches/?$");

    private static final List<String> WEBSOCKET_PATHS = List.of(
            "/ws/",
            "/ws-notifications/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        if (isAlwaysPublic(path) || isWebSocketPath(path) || isPublicAuthPath(path) ||
                (method == HttpMethod.GET && isAnonymousGet(path))) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Authentication required — provide a valid Bearer token");
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = parseToken(token);

            String branchId = claims.get("branchId", String.class);

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.remove(HttpHeaders.AUTHORIZATION);
                        headers.set("X-User-Id", claims.getSubject());
                        headers.set("X-User-Role", claims.get("role", String.class));
                        headers.set("X-User-Email", claims.get("email", String.class));
                        if (branchId != null) {
                            headers.set("X-User-BranchId", branchId);
                        }
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (JwtException e) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token — please log in again");
        }
    }

    private boolean isPublicAuthPath(String path) {
        return path.startsWith("/api/v1/auth/") && !path.startsWith("/api/v1/auth/me");
    }

    private boolean isAlwaysPublic(String path) {
        return ALWAYS_PUBLIC.stream().anyMatch(path::startsWith);
    }

    private boolean isAnonymousGet(String path) {
        if (PUBLIC_BRANCH_LIST.matcher(path).matches()) {
            return true;
        }
        return path.startsWith("/api/v1/branches/nearby");
    }

    private boolean isWebSocketPath(String path) {
        return WEBSOCKET_PATHS.stream().anyMatch(path::startsWith);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", exchange.getRequest().getURI().getPath());
        body.put("timestamp", Instant.now().toString());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            byte[] fallback = ("{\"status\":" + status.value()
                    + ",\"error\":\"" + status.getReasonPhrase()
                    + "\",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\",\"path\":\"" + exchange.getRequest().getURI().getPath() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
