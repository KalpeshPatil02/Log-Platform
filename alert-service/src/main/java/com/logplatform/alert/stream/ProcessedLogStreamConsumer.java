package com.logplatform.alert.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.alert.config.AlertProperties;
import com.logplatform.alert.service.AlertEvaluationService;
import com.logplatform.common.ProcessedLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Reads logs.processed.
 *  - Unparseable event (permanent)  -> logs.dlq + ACK immediately.
 *  - Evaluation failure (transient: DB/Redis down...) -> left PENDING; PendingRecoveryJob retries it
 *    and only dead-letters it after app.alert.max-deliveries attempts.
 * Entries are XDEL'd after XACK so logs.processed does not grow forever (alert-service is its only consumer group).
 */
@Component
public class ProcessedLogStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProcessedLogStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final AlertProperties props;
    private final ObjectMapper mapper;
    private final AlertEvaluationService evaluator;
    private volatile boolean groupReady;

    public ProcessedLogStreamConsumer(
            StringRedisTemplate r,
            AlertProperties p,
            ObjectMapper m,
            AlertEvaluationService e) {
        redis = r;
        props = p;
        mapper = m;
        evaluator = e;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createGroup() {
        try {
            redis.opsForStream().createGroup(
                    props.processedStream(),
                    ReadOffset.from("0-0"),
                    props.consumerGroup()
            );
        } catch (Exception ex) {
            Throwable cause = ex;
            boolean groupExists = false;

            while (cause != null) {
                String message = cause.getMessage();

                if (message != null && message.contains("BUSYGROUP")) {
                    groupExists = true;
                    break;
                }

                cause = cause.getCause();
            }

            if (!groupExists) {
                throw ex;
            }
        }

        groupReady = true;
    }

    @Scheduled(fixedDelayString = "${app.alert.poll-delay-ms:1000}")
    public void poll() {
        if (!groupReady) return;

        try {
            List<MapRecord<String, Object, Object>> records =
                    redis.opsForStream().read(
                            Consumer.from(props.consumerGroup(), props.consumerName()),
                            StreamReadOptions.empty()
                                    .count(20)
                                    .block(Duration.ofMillis(500)),
                            StreamOffset.create(
                                    props.processedStream(),
                                    ReadOffset.lastConsumed()
                            )
                    );

            if (records == null) return;

            for (var rec : records) {
                processRecord(rec);
            }
        } catch (Exception ex) {
            log.error("Polling {} failed: {}", props.processedStream(), ex.toString());
        }
    }

    /** Parse, evaluate, ACK. Also used by PendingRecoveryJob for retries. */
    public void processRecord(MapRecord<String, Object, Object> rec) {
        ProcessedLogEvent event;
        try {
            event = parse(rec);
        } catch (Exception ex) {
            deadLetter(rec, "Invalid event: " + ex.getMessage());
            return;
        }

        try {
            evaluator.evaluate(event);
            acknowledgeAndDelete(rec.getId());
        } catch (Exception ex) {
            log.warn("Evaluation of {} failed, left pending for retry: {}",
                    rec.getId().getValue(), ex.toString());
        }
    }

    private ProcessedLogEvent parse(MapRecord<String, Object, Object> rec) throws Exception {
        Object payload = rec.getValue().get("payload");
        if (payload == null) {
            throw new IllegalArgumentException("missing payload field");
        }
        ProcessedLogEvent event = mapper.readValue(payload.toString(), ProcessedLogEvent.class);
        if (event == null) {
            throw new IllegalArgumentException("empty payload");
        }
        return event;
    }

    /** Copies the record to the DLQ and, only if that worked, ACKs the original. */
    public void deadLetter(MapRecord<String, Object, Object> rec, String reason) {
        try {
            Map<String, String> dlq = new LinkedHashMap<>();
            rec.getValue().forEach((k, v) -> dlq.put(String.valueOf(k), String.valueOf(v)));
            dlq.put("sourceStream", props.processedStream());
            dlq.put("sourceId", rec.getId().getValue());
            dlq.put("error", reason == null ? "unknown" : reason);

            redis.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(props.deadLetterStream())
                            .ofMap(dlq)
            );
            redis.opsForStream().trim(props.deadLetterStream(), 10000L);
            acknowledgeAndDelete(rec.getId());
            log.warn("Dead-lettered {}: {}", rec.getId().getValue(), reason);
        } catch (Exception ex) {
            log.error("DLQ write failed for {}, left pending: {}", rec.getId().getValue(), ex.toString());
        }
    }

    public void acknowledgeAndDelete(RecordId id) {
        redis.opsForStream().acknowledge(props.processedStream(), props.consumerGroup(), id);
        redis.opsForStream().delete(props.processedStream(), id);
    }

    /** For pending ids whose stream entry no longer exists: nothing to retry, just clear the PEL. */
    public void acknowledgeOnly(RecordId id) {
        redis.opsForStream().acknowledge(props.processedStream(), props.consumerGroup(), id);
    }
}
