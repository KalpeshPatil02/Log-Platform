package com.logplatform.notification.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final NotificationProperties props;
    private final ObjectMapper mapper;
    private final NotificationService notificationService;
    private volatile boolean groupReady;

    public NotificationStreamConsumer(StringRedisTemplate redis, NotificationProperties props, ObjectMapper mapper, NotificationService notificationService) {
        this.redis = redis;
        this.props = props;
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createGroup() {
        try {
            redis.opsForStream().createGroup(
                props.stream(),
                ReadOffset.from("0-0"),
                props.consumerGroup()
            );
        } catch (Exception ex) {
            Throwable cause = ex;
            boolean groupExists = false;
            while (cause != null) {
                if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                    groupExists = true;
                    break;
                }
                cause = cause.getCause();
            }
            if (!groupExists) throw ex;
        }
        groupReady = true;
        log.info("Consumer group {} ready on stream {}", props.consumerGroup(), props.stream());
    }

    @Scheduled(fixedDelayString = "${app.notification.poll-delay-ms:1000}")
    public void poll() {
        if (!groupReady) return;
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(props.consumerGroup(), props.consumerName()),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(500)),
                StreamOffset.create(props.stream(), ReadOffset.lastConsumed())
            );
            if (records == null) return;
            for (var rec : records) {
                processRecord(rec);
            }
        } catch (Exception ex) {
            log.error("Polling {} failed: {}", props.stream(), ex.toString());
        }
    }

    void processRecord(MapRecord<String, Object, Object> rec) {
        String eventKey = null;
        String tenantId = null;
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> value = (Map<Object, Object>) rec.getValue();
            eventKey = String.valueOf(value.getOrDefault("eventKey", ""));
            tenantId = String.valueOf(value.getOrDefault("tenantId", ""));
            String payload = String.valueOf(value.getOrDefault("payload", "{}"));

            // Parse payload to determine channel and destination
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) (Object) mapper.readValue(payload, Map.class);
            String channel = String.valueOf(p.getOrDefault("channel", "WEBHOOK"));
            String destination = String.valueOf(p.getOrDefault("destination", props.webhookBaseUrl()));

            notificationService.deliver(eventKey, tenantId, channel, payload, destination);

            redis.opsForStream().acknowledge(props.stream(), props.consumerGroup(), rec.getId());
            redis.opsForStream().delete(props.stream(), rec.getId());
        } catch (Exception ex) {
            log.warn("Failed to process record {}: {}", rec.getId(), ex.toString());
            // Leave in PEL for recovery job to retry
        }
    }

    public String consumerName() {
        return props.consumerName();
    }
}
