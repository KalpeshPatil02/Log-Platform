package com.logplatform.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.processing.domain.NormalizedLog;
import com.logplatform.processing.domain.RawLogEvent;
import com.logplatform.processing.persistence.OutboxEventEntity;
import com.logplatform.processing.persistence.OutboxEventRepository;
import com.logplatform.processing.persistence.ProcessedLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LogProcessingService {

 private final LogNormalizer normalizer;
 private final ProcessedLogRepository logs;
 private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;

 public LogProcessingService(
         LogNormalizer normalizer,
         ProcessedLogRepository logs,
         OutboxEventRepository outbox,
         ObjectMapper mapper
 ) {
  this.normalizer = normalizer;
  this.logs = logs;
  this.outbox = outbox;
  this.mapper = mapper;
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

  } catch (Exception exception) {
   throw new IllegalStateException(
           "Could not process log event", exception
   );
  }
 }
}