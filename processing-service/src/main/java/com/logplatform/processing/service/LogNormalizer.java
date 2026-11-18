package com.logplatform.processing.service;

import com.logplatform.common.Severity;
import com.logplatform.processing.domain.NormalizedLog;
import com.logplatform.processing.domain.RawLogEvent;
import org.springframework.stereotype.Component;

@Component
public class LogNormalizer {

 public NormalizedLog normalize(RawLogEvent event) {
  Severity severity = Severity.parse(event.severity())
          .orElseThrow(() -> new IllegalArgumentException(
                  "Unsupported severity: " + event.severity()
          ));

  String tenantId = event.tenantId().trim();
  String eventId = event.eventId().trim();
  String service = event.service().trim();
  String environment = event.environment().trim();

  if (tenantId.length() > 100) {
   throw new IllegalArgumentException("tenantId exceeds 100 characters");
  }
  if (eventId.length() > 150) {
   throw new IllegalArgumentException("eventId exceeds 150 characters");
  }
  if (service.isBlank() || service.length() > 150) {
   throw new IllegalArgumentException("Invalid service name");
  }
  if (environment.isBlank() || environment.length() > 50) {
   throw new IllegalArgumentException("Invalid environment");
  }
  if (event.message().length() > 16000) {
   throw new IllegalArgumentException("Message exceeds 16000 characters");
  }

  return new NormalizedLog(
          tenantId,
          eventId,
          event.apiKeyId(),
          event.receivedAt(),
          event.timestamp(),
          service,
          environment,
          severity.name(),
          event.message(),
          event.traceId(),
          event.correlationId(),
          event.attributes()
  );
 }
}