package com.logplatform.common;

import java.time.Instant;

/**
 * Message on stream {@code logs.processed}. Written by processing-service after the log row is committed.
 */
public record ProcessedLogEvent(
        String eventId,
        String tenantId,
        Instant timestamp,
        String service,
        String environment,
        Severity severity,
        String message,
        String traceId,
        String fingerprint) {
}
