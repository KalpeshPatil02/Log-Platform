package com.logplatform.ingestion.support;

/** Redis stream field names written by ingestion-service and consumed by processing-service. */
public final class LogFields {
    private LogFields() {}
    public static final String EVENT_ID="eventId", TENANT_ID="tenantId", API_KEY_ID="apiKeyId",
        RECEIVED_AT="receivedAt", TIMESTAMP="timestamp", SERVICE="service", ENVIRONMENT="environment",
        SEVERITY="severity", MESSAGE="message", TRACE_ID="traceId", CORRELATION_ID="correlationId",
        ATTRIBUTES="attributes";
}
