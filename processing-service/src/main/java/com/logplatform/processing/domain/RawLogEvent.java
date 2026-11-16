package com.logplatform.processing.domain;

import java.time.Instant;
import java.util.Map;

public record RawLogEvent(
        String tenantId,
        String eventId,
        String apiKeyId,
        Instant receivedAt,
        Instant timestamp,
        String service,
        String environment,
        String severity,
        String message,
        String traceId,
        String correlationId,
        String attributes
) {
 public static RawLogEvent from(Map<String, String> fields) {
  try {
   return new RawLogEvent(
           required(fields, "tenantId"),
           required(fields, "eventId"),
           required(fields, "apiKeyId"),
           Instant.parse(required(fields, "receivedAt")),
           Instant.parse(required(fields, "timestamp")),
           required(fields, "service"),
           required(fields, "environment"),
           required(fields, "severity"),
           required(fields, "message"),
           optional(fields, "traceId"),
           optional(fields, "correlationId"),
           optional(fields, "attributes")
   );
  } catch (java.time.DateTimeException exception) {
   throw new IllegalArgumentException(
           "Invalid timestamp format in log event",
           exception
   );
  }
 }

 private static String required(
         Map<String, String> fields,
         String key) {

  String value = fields.get(key);

  if (value == null || value.isBlank()) {
   throw new IllegalArgumentException(
           "Missing required field: " + key
   );
  }

  return value;
 }

 private static String optional(
         Map<String, String> fields,
         String key) {

  return fields.get(key);
 }
}