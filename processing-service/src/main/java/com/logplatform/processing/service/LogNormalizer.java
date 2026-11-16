package com.logplatform.processing.service;

import com.logplatform.processing.domain.NormalizedLog;
import com.logplatform.processing.domain.RawLogEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class LogNormalizer {

 private static final Set<String> LEVELS =
         Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

 public NormalizedLog normalize(RawLogEvent event) {
  String severity = event.severity().trim().toUpperCase(Locale.ROOT);

  if (!LEVELS.contains(severity)) {
   throw new IllegalArgumentException(
           "Unsupported severity: " + event.severity()
   );
  }

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
          severity,
          event.message(),
          event.traceId(),
          event.correlationId(),
          event.attributes()
  );
 }
}