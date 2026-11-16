package com.logplatform.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /** Key = client IP. Used for anonymous endpoints (login). */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    /** Key = authenticated user id (JWT sub), falling back to IP. @Primary: the rate-limit filter factory needs one default. */
    @Bean
    @Primary
    KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    // Behind a load balancer this must read a TRUSTED X-Forwarded-For (XForwardedRemoteAddressResolver). Deployment phase.
    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return (remote == null || remote.getAddress() == null) ? "unknown" : remote.getAddress().getHostAddress();
    }
}
