package com.logplatform.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.processing.persistence.ProcessedLogEntity;
import com.logplatform.processing.persistence.ProcessedLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test for processing-service against real MySQL + Redis.
 * Verifies: raw stream → processing → MySQL persistence → DLQ for invalid events.
 */
@SpringBootTest
@Testcontainers
class ProcessingServiceIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass testpass --maxmemory 128mb --maxmemory-policy noeviction --appendonly yes");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "testpass");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProcessedLogRepository processedLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void successfulProcessingFlow() throws Exception {
        // Given: a raw log event on the Redis stream
        Map<String, String> fields = new java.util.HashMap<>();
        fields.put("eventId", "evt-e2e-001");
        fields.put("tenantId", "42");
        fields.put("apiKeyId", "key-42");
        fields.put("receivedAt", Instant.now().toString());
        fields.put("timestamp", Instant.now().toString());
        fields.put("service", "billing-service");
        fields.put("environment", "production");
        fields.put("severity", "ERROR");
        fields.put("message", "Payment failed");
        fields.put("traceId", "trace-001");
        fields.put("spanId", "span-001");
        fields.put("attributes", "{\"orderId\":\"ord-123\"}");

        String json = objectMapper.writeValueAsString(fields);

        // When: publish to logs.raw stream
        redisTemplate.opsForStream().add("logs.raw", Map.of("payload", json, "eventKey", "evt-e2e-001"));

        // Then: the processing consumer should pick it up and write to MySQL
        assertDoesNotThrow(() -> {
            Thread.sleep(5000); // give the scheduled consumer time to poll
        });

        // Verify the log was persisted
        List<ProcessedLogEntity> logs = processedLogRepository.findAll();
        assertThat(logs)
                .filteredOn(e -> "evt-e2e-001".equals(e.getEventId()))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getTenantId()).isEqualTo("42");
                    assertThat(e.getSeverity()).isEqualTo("ERROR");
                    assertThat(e.getMessage()).isEqualTo("Payment failed");
                });
    }

    @Test
    void invalidEventGoesToDeadLetterQueue() throws Exception {
        // Given: an invalid raw log event (missing required fields)
        String invalidJson = "{\"eventId\":\"evt-e2e-002\",\"tenantId\":\"not-a-number\"}";

        // When: publish to logs.raw stream
        redisTemplate.opsForStream().add("logs.raw", Map.of("payload", invalidJson, "eventKey", "evt-e2e-002"));

        // Then: the event should be dead-lettered (not crash the consumer)
        assertDoesNotThrow(() -> {
            Thread.sleep(5000); // give the scheduled consumer time to poll
        });

        // The DLQ should contain the failed event
        Boolean dlqHasEntry = redisTemplate.opsForStream().size("logs.dlq") > 0;
        assertThat(dlqHasEntry).isTrue();
    }
}
