package com.logplatform.processing.domain;

import java.time.Instant;

public record NormalizedLog(
        String tenantId,
        String eventId,
        String apiKeyId,
        Instant receivedAt,
        Instant timestamp,
        String service,
        String environment,
        String severity,
        String message,
        String traceId,
        String correlationId,
        String attributes
) {
}