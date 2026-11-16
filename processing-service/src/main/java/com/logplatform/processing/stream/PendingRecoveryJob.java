package com.logplatform.processing.stream;

import com.logplatform.processing.config.ProcessingProperties;
import com.logplatform.processing.domain.RawLogEvent;
import com.logplatform.processing.service.LogProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class PendingRecoveryJob {

 private static final Logger log =
         LoggerFactory.getLogger(PendingRecoveryJob.class);

 private static final int MAX_RECOVERY_BATCH_SIZE = 100;

 private final StringRedisTemplate redis;
 private final ProcessingProperties properties;
 private final LogProcessingService processor;

 public PendingRecoveryJob(
         StringRedisTemplate redis,
         ProcessingProperties properties,
         LogProcessingService processor) {
  this.redis = redis;
  this.properties = properties;
  this.processor = processor;
 }

 @Scheduled(fixedDelay = 15000)
 public void recover() {
  try {
   PendingMessages pending = redis.opsForStream().pending(
           properties.rawStream(),
           properties.group(),
           Range.unbounded(),
           MAX_RECOVERY_BATCH_SIZE
   );

   if (pending == null) {
    return;
   }

   for (PendingMessage message : pending) {
    if (message.getElapsedTimeSinceLastDelivery()
            .compareTo(Duration.ofMillis(
                    properties.pendingIdleMillis()
            )) < 0) {
     continue;
    }

    recoverMessage(message);
   }

  } catch (Exception exception) {
   log.error("Pending recovery failed", exception);
  }
 }

 private void recoverMessage(PendingMessage message) {
  try {
   var claimed = redis.opsForStream().claim(
           properties.rawStream(),
           properties.group(),
           properties.consumerName(),
           Duration.ofMillis(properties.pendingIdleMillis()),
           message.getId()
   );

   if (claimed == null) {
    return;
   }

   for (MapRecord<String, Object, Object> record : claimed) {
    try {
     Map<String, String> fields =
             toStringMap(record.getValue());

     RawLogEvent event = RawLogEvent.from(fields);

     processor.process(event);
     acknowledgeAndDelete(record.getId());

     log.info(
             "Recovered and processed pending event {}",
             record.getId()
     );

    } catch (IllegalArgumentException
             | DateTimeException exception) {

     log.warn(
             "Invalid pending event {}: {}",
             record.getId(),
             exception.getMessage()
     );

     sendToDeadLetter(record, exception);

    } catch (Exception exception) {
     log.error(
             "Pending event remains unacknowledged: {}",
             record.getId(),
             exception
     );
    }
   }

  } catch (Exception exception) {
   log.warn(
           "Could not recover pending event {}",
           message.getId(),
           exception
   );
  }
 }

 private Map<String, String> toStringMap(
         Map<Object, Object> source) {

  Map<String, String> result = new HashMap<>();

  source.forEach((key, value) -> {
   if (key != null && value != null) {
    result.put(key.toString(), value.toString());
   }
  });

  return result;
 }

 private void acknowledgeAndDelete(RecordId id) {
  redis.opsForStream().acknowledge(
          properties.rawStream(),
          properties.group(),
          id
  );

  redis.opsForStream().delete(
          properties.rawStream(),
          id
  );
 }

 private void sendToDeadLetter(
         MapRecord<String, Object, Object> record,
         Exception cause) {

  try {
   Map<String, String> fields =
           toStringMap(record.getValue());

   fields.put("failureReason", safeMessage(cause));
   fields.put("sourceStreamId", record.getId().getValue());

   redis.opsForStream().add(
           MapRecord.create(
                   properties.deadLetterStream(),
                   fields
           )
   );

   acknowledgeAndDelete(record.getId());

  } catch (Exception exception) {
   log.error(
           "Pending event DLQ publishing failed: {}",
           record.getId(),
           exception
   );
  }
 }

 private String safeMessage(Exception exception) {
  String message = exception.getMessage();

  if (message == null) {
   return "Invalid log event";
  }

  return message.substring(
          0,
          Math.min(message.length(), 900)
  );
 }
}