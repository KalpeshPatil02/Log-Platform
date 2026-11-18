package com.logplatform.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.common.ApiException;
import com.logplatform.ingestion.config.IngestionProperties;
import com.logplatform.ingestion.dto.LogEventRequest;
import com.logplatform.ingestion.security.ApiKeyPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class LogProducerTest {
    static final ApiKeyPrincipal PRINCIPAL = new ApiKeyPrincipal("tenant-1", "key-1");
    @Mock StringRedisTemplate redis;
    @Mock StreamOperations<String, Object, Object> streams;
    @Mock RedisScript<List> script;
    LogProducer producer;
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForStream()).thenReturn(streams);
        IngestionProperties props = new IngestionProperties("http://identity", "t", 100, Duration.ofHours(24),
                new IngestionProperties.Cache(Duration.ofMinutes(5), Duration.ofSeconds(30)), false);
        producer = new LogProducer(redis, new ObjectMapper(), props, script, registry);
    }

    private static LogEventRequest event(String id) {
        return new LogEventRequest(id, "billing", "prod", "ERROR", "payment failed", null, null, null);
    }

    @Test
    void freshEventUsesAtomicPublishScript() {
        when(streams.size("logs.raw")).thenReturn(0L);
        when(redis.execute(eq(script), anyList(), any(Object[].class))).thenReturn(List.of(1L, 0L));
        var result = producer.publish(PRINCIPAL, "corr-12345678", List.of(event("evt-12345678")));
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
        assertThat(result.generatedEventIds()).isZero();
        verify(redis).execute(eq(script), anyList(), any(Object[].class));
    }

    @Test
    void generatedEventIdIsReported() {
        when(streams.size("logs.raw")).thenReturn(0L);
        when(redis.execute(eq(script), anyList(), any(Object[].class))).thenReturn(List.of(1L, 0L));
        var result = producer.publish(PRINCIPAL, null, List.of(event(null)));
        assertThat(result.generatedEventIds()).isEqualTo(1);
        assertThat(result.eventIds()).hasSize(1);
    }

    @Test
    void requireEventIdRejectsMissingIdBeforeRedisWrite() {
        IngestionProperties props = new IngestionProperties("http://identity", "t", 100, Duration.ofHours(24),
                new IngestionProperties.Cache(Duration.ofMinutes(5), Duration.ofSeconds(30)), true);
        producer = new LogProducer(redis, new ObjectMapper(), props, script, registry);
        when(streams.size("logs.raw")).thenReturn(0L);
        assertThatThrownBy(() -> producer.publish(PRINCIPAL, null, List.of(event(null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("eventId is required");
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void backpressureRejectsBeforeScript() {
        when(streams.size("logs.raw")).thenReturn(100L);
        assertThatThrownBy(() -> producer.publish(PRINCIPAL, null, List.of(event(null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("saturated");
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
