package com.logplatform.processing.stream;

import com.logplatform.processing.config.ProcessingProperties;
import com.logplatform.processing.domain.RawLogEvent;
import com.logplatform.processing.service.LogProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RedisStreamConsumer implements ApplicationRunner {

 private static final Logger log =
         LoggerFactory.getLogger(RedisStreamConsumer.class);

 private final StringRedisTemplate redis;
 private final ProcessingProperties properties;
 private final LogProcessingService processor;

 public RedisStreamConsumer(
         StringRedisTemplate redis,
         ProcessingProperties properties,
         LogProcessingService processor) {
  this.redis = redis;
  this.properties = properties;
  this.processor = processor;
 }

 @Override
 public void run(ApplicationArguments args) {
  try {
   redis.opsForStream().createGroup(
           properties.rawStream(),
           ReadOffset.from("0-0"),
           properties.group()
   );

   log.info("Created consumer group {}", properties.group());

  } catch (Exception exception) {
   if (isConsumerGroupAlreadyExists(exception)) {
    log.info(
            "Consumer group {} already exists; reusing it",
            properties.group()
    );
   } else {
    throw exception;
   }
  }
 }

 private boolean isConsumerGroupAlreadyExists(Throwable exception) {
  Throwable current = exception;

  while (current != null) {
   String message = current.getMessage();

   if (message != null && message.contains("BUSYGROUP")) {
    return true;
   }

   current = current.getCause();
  }

  return false;
 }

 @Scheduled(fixedDelay = 500)
 public void poll() {
  try {
   List<MapRecord<String, Object, Object>> records =
           redis.opsForStream().read(
                   Consumer.from(
                           properties.group(),
                           properties.consumerName()
                   ),
                   StreamReadOptions.empty()
                           .count(properties.batchSize())
                           .block(Duration.ofSeconds(1)),
                   StreamOffset.create(
                           properties.rawStream(),
                           ReadOffset.lastConsumed()
                   )
           );

   if (records != null) {
    records.forEach(this::handle);
   }

  } catch (Exception exception) {
   log.error("Redis stream polling failed", exception);
  }
 }

 private void handle(MapRecord<String, Object, Object> record) {
  try {
   Map<String, String> fields =
           toStringMap(record.getValue());

   RawLogEvent event = RawLogEvent.from(fields);

   processor.process(event);

   acknowledgeAndDelete(record.getId());

  } catch (IllegalArgumentException exception) {
   log.warn(
           "Invalid event {}: {}",
           record.getId(),
           exception.getMessage()
   );

   sendToDeadLetter(record, exception);

  } catch (Exception exception) {
   log.error(
           "Processing failed; event remains pending: {}",
           record.getId(),
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
   fields.put(
           "sourceStreamId",
           record.getId().getValue()
   );

   redis.opsForStream().add(
           MapRecord.create(
                   properties.deadLetterStream(),
                   fields
           )
   );

   acknowledgeAndDelete(record.getId());

  } catch (Exception exception) {
   log.error(
           "DLQ publishing failed; source remains pending",
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