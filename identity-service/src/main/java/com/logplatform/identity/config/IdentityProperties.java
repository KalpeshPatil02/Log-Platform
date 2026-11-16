package com.logplatform.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(Jwt jwt, String internalToken, Bootstrap bootstrap) {
    public record Jwt(String issuer, Duration accessTokenTtl, String privateKeyPath, String publicKeyPath) {}
    public record Bootstrap(String adminEmail, String adminPassword) {}
}
