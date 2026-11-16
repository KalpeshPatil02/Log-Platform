package com.logplatform.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/**
 * One log event as sent by a customer application.
 * - There is NO tenant field: tenant comes from the API key, never from the payload.
 * - eventId is OPTIONAL. Send it to make retries safe: the same (tenant, eventId) is accepted only once
 *   within the dedup window. Without it the server assigns a random id (a retry then becomes a new event).
 * - Severity is validated for size only; aliases (WARN/WARNING, ERR/ERROR) are normalised in processing-service.
 */
public record LogEventRequest(
        @Pattern(regexp = "^[A-Za-z0-9._:-]{8,64}$", message = "8-64 chars: letters, digits, . _ : -") String eventId,
        @NotBlank @Size(max = 100) String service,
        @NotBlank @Size(max = 50) String environment,
        @NotBlank @Size(max = 20) String severity,
        @NotBlank @Size(max = 16000) String message,
        Instant timestamp,
        @Size(max = 64) String traceId,
        @Size(max = 50) Map<String, Object> attributes) {}
