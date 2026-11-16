package com.logplatform.query.web;
import com.logplatform.query.domain.ProcessedLogEntity;
import java.time.Instant;
public record LogResponse(String tenantId,String eventId,Instant receivedAt,Instant occurredAt,
 String severity,String serviceName,String environment,String message,String traceId,
 String correlationId,String attributesJson) {
 public static LogResponse from(ProcessedLogEntity e) {
  return new LogResponse(e.getTenantId(),e.getEventId(),e.getReceivedAt(),e.getOccurredAt(),
   e.getSeverity(),e.getServiceName(),e.getEnvironment(),e.getMessage(),e.getTraceId(),
   e.getCorrelationId(),e.getAttributesJson());
 }
}
