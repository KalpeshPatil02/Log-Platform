package com.logplatform.gateway.filter;

import com.logplatform.gateway.security.JsonErrorWriter;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Trust boundary. Downstream services believe X-Tenant-Id / X-User-Id / X-User-Roles,
 * so the gateway (1) ALWAYS strips whatever the client sent and (2) sets them only from the verified JWT.
 *
 * NOTE: never chain switchIfEmpty() after a Mono<Void>-returning step: Mono<Void> always completes empty,
 * so the fallback would run every time. We branch on a non-empty Optional instead.
 */
@Component
public class TenantContextGlobalFilter implements GlobalFilter, Ordered {
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String USER_HEADER = "X-User-Id";
    public static final String ROLES_HEADER = "X-User-Roles";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())          // Mono<Optional<..>> is never empty from here on
                .flatMap(maybeAuth -> maybeAuth.isPresent()
                        ? authenticated(exchange, chain, maybeAuth.get().getToken())
                        : anonymous(exchange, chain));
    }

    private Mono<Void> authenticated(ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            return JsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token has no tenant");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> {
            stripTrustedHeaders(h);
            h.set(TENANT_HEADER, tenantId);
            h.set(USER_HEADER, jwt.getSubject());
            h.set(ROLES_HEADER, roles == null ? "" : String.join(",", roles));
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** Anonymous (e.g. login): still strip anything the client tried to smuggle in. */
    private Mono<Void> anonymous(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(TenantContextGlobalFilter::stripTrustedHeaders).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static void stripTrustedHeaders(HttpHeaders h) {
        h.remove(TENANT_HEADER);
        h.remove(USER_HEADER);
        h.remove(ROLES_HEADER);
        h.remove(INTERNAL_TOKEN_HEADER);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
