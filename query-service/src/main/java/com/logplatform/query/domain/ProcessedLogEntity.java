package com.logplatform.query.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="processed_logs")
public class ProcessedLogEntity {
 @Id private Long id;
 @Column(name="tenant_id",nullable=false) private String tenantId;
 @Column(name="event_id",nullable=false) private String eventId;
 @Column(name="api_key_id",nullable=false) private String apiKeyId;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 @Column(nullable=false) private String severity;
 @Column(name="service_name",nullable=false) private String serviceName;
 @Column(nullable=false) private String environment;
 @Column(nullable=false,columnDefinition="text") private String message;
 @Column(name="trace_id") private String traceId;
 @Column(name="correlation_id") private String correlationId;
 @Column(name="attributes_json",columnDefinition="json") private String attributesJson;
 protected ProcessedLogEntity() {}
 public Long getId(){return id;} public String getTenantId(){return tenantId;}
 public String getEventId(){return eventId;} public Instant getReceivedAt(){return receivedAt;}
 public Instant getOccurredAt(){return occurredAt;} public String getSeverity(){return severity;}
 public String getServiceName(){return serviceName;} public String getEnvironment(){return environment;}
 public String getMessage(){return message;} public String getTraceId(){return traceId;}
 public String getCorrelationId(){return correlationId;} public String getAttributesJson(){return attributesJson;}
}
