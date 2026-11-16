package com.logplatform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Jwt jwt) {
    public record Jwt(String issuer, String jwksUri) {}
}
