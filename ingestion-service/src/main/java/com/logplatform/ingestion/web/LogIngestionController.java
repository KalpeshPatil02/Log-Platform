package com.logplatform.ingestion.web;

import org.slf4j.MDC;
import com.logplatform.web.CorrelationIdFilter;
import com.logplatform.ingestion.dto.BatchRequest;
import com.logplatform.ingestion.dto.IngestResponse;
import com.logplatform.ingestion.dto.LogEventRequest;
import com.logplatform.ingestion.security.ApiKeyAuthInterceptor;
import com.logplatform.ingestion.security.ApiKeyPrincipal;
import com.logplatform.ingestion.service.LogProducer;
import com.logplatform.ingestion.service.LogProducer.PublishResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 202 Accepted = "queued", not "stored". Storage happens asynchronously in processing-service. */
@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {
    private final LogProducer producer;

    public LogIngestionController(LogProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestResponse ingest(@Valid @RequestBody LogEventRequest event,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                 @RequestAttribute(ApiKeyAuthInterceptor.ATTR_PRINCIPAL) ApiKeyPrincipal principal) {
        LogEventRequest effective = event;
        if (event.eventId() == null || event.eventId().isBlank()) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (!idempotencyKey.trim().matches("^[A-Za-z0-9._:-]{8,64}$")) {
                    throw com.logplatform.common.ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                            "Idempotency-Key must contain 8-64 allowed characters");
                }
                effective = new LogEventRequest(idempotencyKey.trim(), event.service(), event.environment(),
                        event.severity(), event.message(), event.timestamp(), event.traceId(), event.attributes());
            }
        } else if (idempotencyKey != null && !idempotencyKey.isBlank()
                && !event.eventId().trim().equals(idempotencyKey.trim())) {
            throw com.logplatform.common.ApiException.badRequest("IDEMPOTENCY_KEY_MISMATCH",
                    "Idempotency-Key must match eventId when both are supplied");
        }
        return toResponse(producer.publish(principal, MDC.get(CorrelationIdFilter.MDC_KEY), List.of(effective)));
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestResponse ingestBatch(@Valid @RequestBody BatchRequest batch,
                                      @RequestAttribute(ApiKeyAuthInterceptor.ATTR_PRINCIPAL) ApiKeyPrincipal principal) {
        return toResponse(producer.publish(principal, MDC.get(CorrelationIdFilter.MDC_KEY), batch.events()));
    }

    private static IngestResponse toResponse(PublishResult r) {
        return new IngestResponse(r.accepted(), r.duplicates(), r.generatedEventIds(), r.eventIds());
    }
}
