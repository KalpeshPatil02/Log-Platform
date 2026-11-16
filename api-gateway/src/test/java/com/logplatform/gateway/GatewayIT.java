package com.logplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/** Needs Docker (real Redis). identity-service is NOT running, so proxied calls end in 5xx - that's fine here. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewayIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockBean ReactiveJwtDecoder jwtDecoder;
    @Autowired WebTestClient client;

    @Test
    void protectedRouteWithoutTokenIs401AndCarriesCorrelationId() {
        client.get().uri("/api/v1/users").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void internalEndpointsAreNotRoutedEvenWithAValidToken() {
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "RS256").subject("u1")
                .claim("tenant_id", "t1").claim("roles", List.of("TENANT_ADMIN"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("good")).thenReturn(Mono.just(jwt));

        client.post().uri("/internal/api-keys/validate")
                .header("Authorization", "Bearer good")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void loginIsRateLimitedPerIp() {
        boolean limited = false;
        for (int i = 0; i < 40 && !limited; i++) {
            int status = client.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange().returnResult(Void.class).getStatus().value();
            limited = status == 429;
        }
        assertThat(limited).as("expected a 429 within 40 rapid login attempts").isTrue();
    }
}
