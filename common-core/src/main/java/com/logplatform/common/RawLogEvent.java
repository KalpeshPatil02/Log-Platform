package com.logplatform.common;

import java.time.Instant;
import java.util.Map;

/**
 * Message on stream {@code logs.raw}. Written by ingestion-service after validation; tenantId comes from the API key, never from the client.
 */
public record RawLogEvent(
        String eventId,
        long tenantId,
        Instant receivedAt,
        String service,
        String environment,
        String severity,
        String message,
        Instant timestamp,
        String traceId,
        String spanId,
        Map<String, String> attributes) {
}
