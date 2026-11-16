package com.logplatform.processing.outbox;

import com.logplatform.processing.config.ProcessingProperties;
import com.logplatform.processing.persistence.OutboxEventEntity;
import com.logplatform.processing.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class OutboxPublisher {

 private static final Logger log =
         LoggerFactory.getLogger(OutboxPublisher.class);

 private final OutboxEventRepository repository;
 private final StringRedisTemplate redis;
 private final ProcessingProperties properties;
 private final OutboxTransactionService transactionService;

 public OutboxPublisher(
         OutboxEventRepository repository,
         StringRedisTemplate redis,
         ProcessingProperties properties,
         OutboxTransactionService transactionService) {
  this.repository = repository;
  this.redis = redis;
  this.properties = properties;
  this.transactionService = transactionService;
 }

 @Scheduled(fixedDelay = 1000)
 public void publish() {
  var events =
          repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                  "PENDING",
                  Instant.now()
          );

  for (OutboxEventEntity event : events) {
   publishOne(event);
  }
 }

 private void publishOne(OutboxEventEntity event) {
  try {
   redis.opsForStream().add(
           MapRecord.create(
                   properties.processedStream(),
                   Map.of(
                           "eventKey", event.getEventKey(),
                           "payload", event.getPayload()
                   )
           )
   );

   transactionService.markPublished(event.getId());

   log.debug("Published outbox event {}", event.getEventKey());

  } catch (Exception exception) {
   transactionService.markFailed(
           event.getId(),
           exception.getMessage()
   );

   log.error(
           "Outbox publish failed for {}",
           event.getEventKey(),
           exception
   );
  }
 }
}