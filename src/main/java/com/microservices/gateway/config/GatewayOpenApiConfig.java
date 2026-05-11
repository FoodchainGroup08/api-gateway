package com.microservices.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GatewayOpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FoodChain API — Complete Reference")
                        .description("All FoodChain microservice endpoints " +
                                "aggregated through the API Gateway. " +
                                "Select a service from the dropdown above.")
                        .version("v1.0.0"))
                .servers(List.of(
                        new Server()
                                .url("http://54.235.78.18:8080")
                                .description("Production (Live API Gateway)"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("API Gateway (local dev)")));
    }
}