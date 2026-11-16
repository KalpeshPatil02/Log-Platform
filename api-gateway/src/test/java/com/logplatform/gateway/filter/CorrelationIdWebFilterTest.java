package com.logplatform.gateway.filter;

import static com.logplatform.gateway.filter.CorrelationIdWebFilter.HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class CorrelationIdWebFilterTest {
    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    private String run(MockServerHttpRequest req, AtomicReference<ServerWebExchange> seen) {
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        WebFilterChain chain = e -> { seen.set(e); return Mono.empty(); };
        filter.filter(ex, chain).block();
        return ex.getResponse().getHeaders().getFirst(HEADER);
    }

    @Test
    void generatesIdWhenMissingAndPropagatesDownstreamAndToResponse() {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        String id = run(MockServerHttpRequest.get("/x").build(), seen);

        assertThat(id).isNotBlank();
        assertThat(seen.get().getRequest().getHeaders().getFirst(HEADER)).isEqualTo(id);
    }

    @Test
    void keepsWellFormedClientId() {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        String id = run(MockServerHttpRequest.get("/x").header(HEADER, "trace-12345678").build(), seen);

        assertThat(id).isEqualTo("trace-12345678");
    }

    @Test
    void replacesSuspiciousClientId() {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        String id = run(MockServerHttpRequest.get("/x").header(HEADER, "<script>alert(1)</script>").build(), seen);

        assertThat(id).isNotEqualTo("<script>alert(1)</script>").matches("^[0-9a-f-]{36}$");
    }
}
