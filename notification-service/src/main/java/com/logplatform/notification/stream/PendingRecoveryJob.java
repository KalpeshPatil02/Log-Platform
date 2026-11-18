package com.logplatform.notification.stream;

import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PendingRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRecoveryJob.class);
    private static final int BATCH = 100;

    private final StringRedisTemplate redis;
    private final NotificationProperties props;
    private final NotificationStreamConsumer consumer;

    public PendingRecoveryJob(StringRedisTemplate redis, NotificationProperties props, NotificationStreamConsumer consumer) {
        this.redis = redis;
        this.props = props;
        this.consumer = consumer;
    }

    @Scheduled(fixedDelayString = "${app.notification.recovery-delay-ms:15000}")
    public void recover() {
        try {
            PendingMessages pending = redis.opsForStream().pending(
                props.stream(), props.consumerGroup(), Range.unbounded(), BATCH);
            if (pending == null) return;

            Duration idle = Duration.ofMillis(props.pendingIdleMs());
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(idle) < 0) continue;
                recoverOne(message);
            }
        } catch (Exception ex) {
            log.error("Pending recovery failed: {}", ex.toString());
        }
    }

    private void recoverOne(PendingMessage message) {
        try {
            var claimed = redis.opsForStream().claim(
                props.stream(), props.consumerGroup(), props.consumerName(), idle(), message.getId());

            if (claimed == null || claimed.isEmpty()) {
                // Entry deleted from stream; clear from PEL
                redis.opsForStream().acknowledge(props.stream(), props.consumerGroup(), message.getId());
                return;
            }

            for (var rec : claimed) {
                consumer.processRecord(rec);
            }
        } catch (Exception ex) {
            log.warn("Could not recover pending event {}: {}", message.getId(), ex.toString());
        }
    }

    private Duration idle() {
        return Duration.ofMillis(props.pendingIdleMs());
    }
}
