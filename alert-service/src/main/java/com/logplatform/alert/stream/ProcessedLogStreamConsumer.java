package com.logplatform.alert.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.alert.config.AlertProperties;
import com.logplatform.alert.service.AlertEvaluationService;
import com.logplatform.common.ProcessedLogEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class ProcessedLogStreamConsumer {

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
            process(rec);
        }
    }

    private void process(MapRecord<String, Object, Object> rec) {
        try {
            Map<String, Object> m = new HashMap<>();
            rec.getValue().forEach(
                    (k, v) -> m.put(String.valueOf(k), v)
            );

            ProcessedLogEvent event = mapper.readValue(
                    String.valueOf(m.get("payload")),
                    ProcessedLogEvent.class
            );

            evaluator.evaluate(event);

            redis.opsForStream().acknowledge(
                    props.processedStream(),
                    props.consumerGroup(),
                    rec.getId()
            );

        } catch (Exception ex) {
            try {
                Map<String, String> dlq = new LinkedHashMap<>();

                dlq.put("sourceStream", props.processedStream());
                dlq.put("sourceId", rec.getId().getValue());
                dlq.put("error", String.valueOf(ex.getMessage()));

                rec.getValue().forEach(
                        (k, v) -> dlq.put(
                                String.valueOf(k),
                                String.valueOf(v)
                        )
                );

                redis.opsForStream().add(
                        StreamRecords.newRecord()
                                .in(props.deadLetterStream())
                                .ofMap(dlq)
                );

                redis.opsForStream().acknowledge(
                        props.processedStream(),
                        props.consumerGroup(),
                        rec.getId()
                );

            } catch (Exception ignored) {
                /* leave pending for redelivery */
            }
        }
    }
}
