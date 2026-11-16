package com.logplatform.gateway.security;

import com.logplatform.gateway.filter.CorrelationIdWebFilter;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Uniform JSON errors produced by the gateway itself. Messages must be constants (no user input). */
public final class JsonErrorWriter {
    private JsonErrorWriter() {}

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String cid = res.getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        String body = "{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"correlationId\":%s}"
                .formatted(status.value(), code, message, cid == null ? "null" : "\"" + cid + "\"");
        DataBuffer buffer = res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buffer));
    }
}
