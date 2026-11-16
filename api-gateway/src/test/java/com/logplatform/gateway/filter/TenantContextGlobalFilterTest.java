package com.logplatform.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class TenantContextGlobalFilterTest {
    private final TenantContextGlobalFilter filter = new TenantContextGlobalFilter();
    private final AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final GatewayFilterChain chain = e -> { calls.incrementAndGet(); seen.set(e); return Mono.empty(); };

    private MockServerWebExchange spoofedRequest() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users")
                .header("X-Tenant-Id", "evil-tenant")
                .header("X-User-Id", "evil-user")
                .header("X-User-Roles", "PLATFORM_ADMIN")
                .header("X-Internal-Token", "stolen")
                .build());
    }

    private Authentication auth(String tenantId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("DEVELOPER", "VIEWER"));
        if (tenantId != null) b.claim("tenant_id", tenantId);
        return new JwtAuthenticationToken(b.build());
    }

    @Test
    void spoofedHeadersAreReplacedByVerifiedJwtClaims() {
        filter.filter(spoofedRequest(), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth("tenant-1")))
                .block();

        HttpHeaders h = seen.get().getRequest().getHeaders();
        assertThat(h.get("X-Tenant-Id")).containsExactly("tenant-1");
        assertThat(h.get("X-User-Id")).containsExactly("user-1");
        assertThat(h.get("X-User-Roles")).containsExactly("DEVELOPER,VIEWER");
        assertThat(h.containsKey("X-Internal-Token")).isFalse();
        assertThat(calls.get()).as("chain must run exactly once").isEqualTo(1);
    }

    @Test
    void anonymousRequestsHaveSpoofedHeadersStripped() {
        filter.filter(spoofedRequest(), chain).block();

        HttpHeaders h = seen.get().getRequest().getHeaders();
        assertThat(h.containsKey("X-Tenant-Id")).isFalse();
        assertThat(h.containsKey("X-User-Id")).isFalse();
        assertThat(h.containsKey("X-User-Roles")).isFalse();
        assertThat(h.containsKey("X-Internal-Token")).isFalse();
        assertThat(calls.get()).as("chain must run exactly once").isEqualTo(1);
    }

    @Test
    void tokenWithoutTenantClaimIsRejectedAndNeverForwarded() {
        MockServerWebExchange ex = spoofedRequest();
        filter.filter(ex, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(null)))
                .block();

        assertThat(calls.get()).as("rejected request must never reach the chain").isZero();
        assertThat(seen.get()).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
