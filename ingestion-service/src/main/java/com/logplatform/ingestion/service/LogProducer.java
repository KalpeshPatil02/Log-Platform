package com.logplatform.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.common.ApiException;
import com.logplatform.ingestion.exception.BackpressureException;
import com.logplatform.common.Streams;
import com.logplatform.ingestion.support.CacheKeys;
import com.logplatform.ingestion.support.LogFields;
import com.logplatform.ingestion.config.IngestionProperties;
import com.logplatform.ingestion.dto.LogEventRequest;
import com.logplatform.ingestion.security.ApiKeyPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Validates, stamps with trusted tenant context, de-duplicates by (tenant, eventId) and appends to logs.raw (XADD).
 * Delivery guarantee: AT-LEAST-ONCE. Exactly-once-ish only for clients that send their own eventId.
 */
@Service
public class LogProducer {
    private static final Logger log = LoggerFactory.getLogger(LogProducer.class);
    private static final int MAX_ATTRIBUTES_BYTES = 8192;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final IngestionProperties props;
    private final RedisScript<List> atomicPublishScript;

    public LogProducer(StringRedisTemplate redis, ObjectMapper mapper, IngestionProperties props, RedisScript<List> atomicPublishScript) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
        this.atomicPublishScript = atomicPublishScript;
    }

    public record PublishResult(int accepted, int duplicates, int generatedEventIds, List<String> eventIds) {}

    /** Validates the entire request before executing one Redis Lua operation for the batch. */
    public PublishResult publish(ApiKeyPrincipal principal, String correlationId, List<LogEventRequest> events) {
        checkBackpressure();
        if (props.requireEventId() && events.stream().anyMatch(e -> e.eventId() == null || e.eventId().isBlank())) {
            throw ApiException.badRequest("EVENT_ID_REQUIRED", "eventId is required");
        }

        Instant receivedAt = Instant.now();
        List<Map<String, String>> records = new ArrayList<>(events.size());
        int generated = 0;
        for (LogEventRequest e : events) {
            if (e.eventId() == null || e.eventId().isBlank()) generated++;
            records.add(toRecord(principal, correlationId, receivedAt, e));
        }

        // Collapse repeated IDs within this request; only one Redis dedup key per event.
        Map<String, Map<String, String>> unique = new LinkedHashMap<>();
        int inRequestDuplicates = 0;
        for (Map<String, String> record : records) {
            if (unique.putIfAbsent(record.get(LogFields.EVENT_ID), record) != null) inRequestDuplicates++;
        }

        List<String> keys = new ArrayList<>();
        keys.add(Streams.LOGS_RAW);
        List<String> args = new ArrayList<>();
        args.add(Long.toString(Math.max(1, props.dedupTtl().toSeconds())));
        args.add(Integer.toString(unique.size()));
        for (Map<String, String> record : unique.values()) {
            keys.add(CacheKeys.dedup(principal.tenantId(), record.get(LogFields.EVENT_ID)));
            try {
                args.add(mapper.writeValueAsString(record));
            } catch (JsonProcessingException ex) {
                throw ApiException.badRequest("INVALID_EVENT", "event could not be serialised");
            }
        }

        List<?> result = redis.execute(atomicPublishScript, keys, args.toArray());
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis atomic publish returned an invalid result");
        }
        int accepted = Integer.parseInt(result.get(0).toString());
        int duplicates = Integer.parseInt(result.get(1).toString()) + inRequestDuplicates;
        return new PublishResult(accepted, duplicates, generated, records.stream()
                .map(r -> r.get(LogFields.EVENT_ID)).toList());
    }

    /**
     * Backpressure: at the high-water mark we refuse new work (503 + Retry-After) instead of letting Redis memory
     * grow without bound. XLEN counts acknowledged entries too, so processing-service (Phase 4) must XDEL entries
     * after XACK for this number to mean "backlog".
     */
    private void checkBackpressure() {
        Long length = redis.opsForStream().size(Streams.LOGS_RAW);
        if (length != null && length >= props.maxStreamLength()) {
            throw new BackpressureException();
        }
    }

    private Map<String, String> toRecord(ApiKeyPrincipal p, String correlationId, Instant receivedAt, LogEventRequest e) {
        Instant ts = e.timestamp() != null ? e.timestamp() : receivedAt;
        if (ts.isAfter(receivedAt.plus(Duration.ofHours(24)))) {
            throw ApiException.badRequest("INVALID_TIMESTAMP", "timestamp is too far in the future");
        }

        Map<String, String> m = new LinkedHashMap<>();
        m.put(LogFields.EVENT_ID, (e.eventId() != null && !e.eventId().isBlank()) ? e.eventId().trim()
                : UUID.randomUUID().toString());
        m.put(LogFields.TENANT_ID, p.tenantId());
        m.put(LogFields.API_KEY_ID, p.apiKeyId());
        m.put(LogFields.RECEIVED_AT, receivedAt.toString());
        m.put(LogFields.TIMESTAMP, ts.toString());
        m.put(LogFields.SERVICE, e.service().trim());
        m.put(LogFields.ENVIRONMENT, e.environment().trim());
        m.put(LogFields.SEVERITY, e.severity().trim());
        m.put(LogFields.MESSAGE, e.message());
        if (e.traceId() != null && !e.traceId().isBlank()) {
            m.put(LogFields.TRACE_ID, e.traceId().trim());
        }
        if (correlationId != null && !correlationId.isBlank()) {
            m.put(LogFields.CORRELATION_ID, correlationId.length() > 64 ? correlationId.substring(0, 64) : correlationId);
        }
        if (e.attributes() != null && !e.attributes().isEmpty()) {
            m.put(LogFields.ATTRIBUTES, attributesJson(e.attributes()));
        }
        return m;
    }

    private String attributesJson(Map<String, Object> attributes) {
        try {
            String json = mapper.writeValueAsString(attributes);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ATTRIBUTES_BYTES) {
                throw ApiException.badRequest("ATTRIBUTES_TOO_LARGE",
                        "attributes must be at most " + MAX_ATTRIBUTES_BYTES + " bytes as JSON");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("INVALID_ATTRIBUTES", "attributes could not be serialised");
        }
    }
}
