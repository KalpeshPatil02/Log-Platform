package com.logplatform.processing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedLogRepository
        extends JpaRepository<ProcessedLogEntity, Long> {

 @Modifying
 @Query(value = """
            INSERT INTO processed_logs (
                tenant_id, event_id, api_key_id, received_at,
                occurred_at, severity, service_name, environment,
                message, trace_id, correlation_id, attributes_json
            )
            VALUES (
                :tenantId, :eventId, :apiKeyId, :receivedAt,
                :occurredAt, :severity, :service, :environment,
                :message, :traceId, :correlationId, :attributes
            )
            ON DUPLICATE KEY UPDATE event_id = event_id
            """, nativeQuery = true)
 int insertIfAbsent(
         @Param("tenantId") String tenantId,
         @Param("eventId") String eventId,
         @Param("apiKeyId") String apiKeyId,
         @Param("receivedAt") Instant receivedAt,
         @Param("occurredAt") Instant occurredAt,
         @Param("severity") String severity,
         @Param("service") String service,
         @Param("environment") String environment,
         @Param("message") String message,
         @Param("traceId") String traceId,
         @Param("correlationId") String correlationId,
         @Param("attributes") String attributes
 );
}