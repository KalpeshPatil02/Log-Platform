package com.logplatform.alert.stream;

import com.logplatform.alert.config.AlertProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Re-delivers events that were read but never ACKed (service crashed, or evaluation failed because
 * MySQL/Redis was briefly down). After app.alert.max-deliveries attempts the event goes to the DLQ.
 */
@Component
public class PendingRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRecoveryJob.class);
    private static final int BATCH = 100;

    private final StringRedisTemplate redis;
    private final AlertProperties props;
    private final ProcessedLogStreamConsumer consumer;

    public PendingRecoveryJob(StringRedisTemplate redis, AlertProperties props, ProcessedLogStreamConsumer consumer) {
        this.redis = redis;
        this.props = props;
        this.consumer = consumer;
    }

    @Scheduled(fixedDelayString = "${app.alert.recovery-delay-ms:15000}")
    public void recover() {
        try {
            PendingMessages pending = redis.opsForStream().pending(
                    props.processedStream(), props.consumerGroup(), Range.unbounded(), BATCH);
            if (pending == null) return;

            Duration idle = Duration.ofMillis(props.pendingIdleMs());
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(idle) < 0) continue;
                recoverOne(message, idle);
            }
        } catch (Exception ex) {
            log.error("Pending recovery failed: {}", ex.toString());
        }
    }

    private void recoverOne(PendingMessage message, Duration idle) {
        try {
            long deliveries = message.getTotalDeliveryCount();

            var claimed = redis.opsForStream().claim(
                    props.processedStream(), props.consumerGroup(), props.consumerName(), idle, message.getId());

            if (claimed == null || claimed.isEmpty()) {
                // entry was deleted from the stream; it can never be processed, so clear it from the PEL
                consumer.acknowledgeOnly(message.getId());
                return;
            }

            for (MapRecord<String, Object, Object> rec : claimed) {
                if (deliveries >= props.maxDeliveries()) {
                    consumer.deadLetter(rec, "Gave up after " + deliveries + " delivery attempts");
                } else {
                    log.info("Retrying pending event {} (delivery {})", rec.getId().getValue(), deliveries + 1);
                    consumer.processRecord(rec);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not recover pending event {}: {}", message.getId(), ex.toString());
        }
    }
}
