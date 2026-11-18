package com.logplatform.processing.service;

import com.logplatform.processing.domain.NormalizedLog;
import com.logplatform.processing.domain.RawLogEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LogNormalizerTest {

 private final LogNormalizer normalizer = new LogNormalizer();

 private RawLogEvent validEvent(String severity) {
  Instant now = Instant.parse("2026-11-03T08:00:00Z");

  return new RawLogEvent(
          "tenant-1",
          "event-1",
          "api-key-1",
          now,
          now,
          "payment-service",
          "production",
          severity,
          "Payment completed",
          "trace-123",
          "correlation-123",
          "{\"orderId\":\"order-123\"}"
  );
 }

 @Test
 void shouldNormalizeSeverityToUppercase() {
  NormalizedLog result = normalizer.normalize(validEvent("error"));

  assertEquals("ERROR", result.severity());
 }

 @Test
 void shouldPreserveValidEventFields() {
  RawLogEvent raw = validEvent("INFO");

  NormalizedLog result = normalizer.normalize(raw);

  assertEquals(raw.tenantId(), result.tenantId());
  assertEquals(raw.eventId(), result.eventId());
  assertEquals(raw.apiKeyId(), result.apiKeyId());
  assertEquals(raw.service(), result.service());
  assertEquals(raw.environment(), result.environment());
  assertEquals(raw.message(), result.message());
  assertEquals(raw.traceId(), result.traceId());
  assertEquals(raw.correlationId(), result.correlationId());
  assertEquals(raw.attributes(), result.attributes());
 }

 @Test
 void shouldNormalizeWarningAliasToWarn() {
  NormalizedLog result = normalizer.normalize(validEvent("WARNING"));

  assertEquals("WARN", result.severity());
 }

 @Test
 void shouldNormalizeErrAliasToError() {
  NormalizedLog result = normalizer.normalize(validEvent("ERR"));

  assertEquals("ERROR", result.severity());
 }

 @Test
 void shouldRejectUnsupportedSeverity() {
  RawLogEvent raw = validEvent("UNKNOWN");

  assertThrows(
          IllegalArgumentException.class,
          () -> normalizer.normalize(raw)
  );
 }
}