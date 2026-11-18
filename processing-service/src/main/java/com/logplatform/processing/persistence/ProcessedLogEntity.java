package com.logplatform.processing.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "processed_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_tenant_event",
                columnNames = {"tenant_id", "event_id"}
        ),
        indexes = {
                @Index(name = "idx_processed_tenant_time",
                        columnList = "tenant_id,occurred_at"),
                @Index(name = "idx_processed_tenant_level",
                        columnList = "tenant_id,severity")
        }
)
public class ProcessedLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "api_key_id", nullable = false, length = 100)
    private String apiKeyId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(name = "service_name", nullable = false, length = 150)
    private String service;

    @Column(nullable = false, length = 50)
    private String environment;

    @Column(nullable = false, length = 16000)
    private String message;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "attributes_json", columnDefinition = "json")
    private String attributes;

    protected ProcessedLogEntity() {
    }

    public ProcessedLogEntity(
            String tenantId,
            String eventId,
            String apiKeyId,
            Instant receivedAt,
            Instant occurredAt,
            String severity,
            String service,
            String environment,
            String message,
            String traceId,
            String correlationId,
            String attributes
    ) {
        this.tenantId = tenantId;
        this.eventId = eventId;
        this.apiKeyId = apiKeyId;
        this.receivedAt = receivedAt;
        this.occurredAt = occurredAt;
        this.severity = severity;
        this.service = service;
        this.environment = environment;
        this.message = message;
        this.traceId = traceId;
        this.correlationId = correlationId;
        this.attributes = attributes;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getSeverity() {
        return severity;
    }

    public String getService() {
        return service;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getAttributes() {
        return attributes;
    }
}
