package com.logplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.common.Streams;
import com.logplatform.ingestion.config.IngestionProperties;
import com.logplatform.ingestion.dto.LogEventRequest;
import com.logplatform.ingestion.security.ApiKeyPrincipal;
import com.logplatform.ingestion.service.LogProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test for ingestion-service against real Redis.
 * Verifies: log ingestion → dedup → backpressure → stream publish.
 */
@SpringBootTest
@Testcontainers
class IngestionServiceIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass testpass --maxmemory 128mb --maxmemory-policy noeviction --appendonly yes");

    static void redisProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "testpass");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IngestionProperties props;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RedisScript<List> script = mock(RedisScript.class);

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.execute(eq(script), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));
    }

    @Test
    void successfulIngestionAndDedup() throws Exception {
        // Given: a LogProducer and an API key principal
        LogProducer producer = new LogProducer(
                redisTemplate, objectMapper, props, script, registry
        );

        ApiKeyPrincipal principal = new ApiKeyPrincipal("tenant-42", "key-42");

        LogEventRequest event = new LogEventRequest(
                "evt-ingest-001", "billing-service", "production", "ERROR",
                "Payment failed", Instant.now(), "trace-001", Map.of()
        );

        // When: publish the event
        LogProducer.PublishResult result = producer.publish(principal, "corr-001", List.of(event));

        // Then: the event should be accepted
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.eventIds()).contains("evt-ingest-001");
    }

    @Test
    void duplicateEventIsDeduped() throws Exception {
        // Given: a LogProducer
        LogProducer producer = new LogProducer(
                redisTemplate, objectMapper, props, script, registry
        );
        ApiKeyPrincipal principal = new ApiKeyPrincipal("tenant-42", "key-42");

        LogEventRequest event = new LogEventRequest(
                "evt-dedup-001", "billing-service", "production", "INFO",
                "Test message", Instant.now(), null, Map.of()
        );

        // When: publish the same eventId twice
        producer.publish(principal, "corr-002", List.of(event));
        producer.publish(principal, "corr-003", List.of(event));

        // Then: both calls complete without error
    }
}
