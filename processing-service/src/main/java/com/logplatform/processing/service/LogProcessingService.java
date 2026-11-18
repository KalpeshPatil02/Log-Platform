package com.logplatform.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.processing.domain.NormalizedLog;
import com.logplatform.processing.domain.RawLogEvent;
import com.logplatform.processing.persistence.OutboxEventEntity;
import com.logplatform.processing.persistence.OutboxEventRepository;
import com.logplatform.processing.persistence.ProcessedLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LogProcessingService {

 private final LogNormalizer normalizer;
 private final ProcessedLogRepository logs;
 private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;
 private final Counter processedTotal;
 private final Counter processedFailed;

 public LogProcessingService(
         LogNormalizer normalizer,
         ProcessedLogRepository logs,
         OutboxEventRepository outbox,
         ObjectMapper mapper,
         MeterRegistry registry
 ) {
  this.normalizer = normalizer;
  this.logs = logs;
  this.outbox = outbox;
  this.mapper = mapper;
  this.processedTotal = Counter.builder("logs.processed.total")
          .tag("application", "processing-service")
          .register(registry);
  this.processedFailed = Counter.builder("logs.processed.failed")
          .tag("application", "processing-service")
          .register(registry);
 }

 @Transactional
 public void process(RawLogEvent raw) {
  NormalizedLog log = normalizer.normalize(raw);

  try {
   String payload = mapper.writeValueAsString(log);
   Instant now = Instant.now();

   outbox.insertIfAbsent(
           log.tenantId() + ":" + log.eventId(),
           payload,
           now
   );

   logs.insertIfAbsent(
           log.tenantId(),
           log.eventId(),
           log.apiKeyId(),
           log.receivedAt(),
           log.timestamp(),
           log.severity(),
           log.service(),
           log.environment(),
           log.message(),
           log.traceId(),
           log.correlationId(),
           log.attributes()
   );

   processedTotal.increment();

  } catch (Exception exception) {
   processedFailed.increment();
   throw new IllegalStateException(
           "Could not process log event", exception
   );
  }
 }
}