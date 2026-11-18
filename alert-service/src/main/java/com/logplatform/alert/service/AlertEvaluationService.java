package com.logplatform.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.alert.config.AlertProperties;
import com.logplatform.alert.domain.*;
import com.logplatform.alert.repository.*;
import com.logplatform.common.ProcessedLogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class AlertEvaluationService {
    private final RuleRepository rules;
    private final IncidentRepository incidents;
    private final NotificationOutboxRepository outbox;
    private final SlidingWindowCounter counter;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AlertProperties props;
    private final Timer evaluationTimer;
    private final Counter incidentsCreated;

    public AlertEvaluationService(RuleRepository r, IncidentRepository i, NotificationOutboxRepository o, SlidingWindowCounter c, StringRedisTemplate redis, ObjectMapper m, AlertProperties p, MeterRegistry registry) {
        rules = r;
        incidents = i;
        outbox = o;
        counter = c;
        this.redis = redis;
        mapper = m;
        props = p;
        this.evaluationTimer = Timer.builder("alert.evaluation.duration")
                .tag("application", "alert-service")
                .register(registry);
        this.incidentsCreated = Counter.builder("alerts.incidents.created")
                .tag("application", "alert-service")
                .register(registry);
    }

    @Transactional
    public void evaluate(ProcessedLogEvent e) {
        Timer.Sample sample = Timer.start();
        try {
            if (e == null || e.tenantId() == null || e.tenantId().isBlank() || e.tenantId().length() > 36 || e.eventId() == null || e.timestamp() == null || e.severity() == null)
                return;
            for (RuleEntity rule : rules.findByTenantIdAndEnabledTrue(e.tenantId())) {
                if (!matches(rule, e)) continue;
                String fp = fingerprint(e, rule);
                String key = props.windowKeyPrefix() + e.tenantId() + ":" + rule.getId() + ":" + fp;
                long count = counter.addAndCount(key, e.eventId(), e.timestamp(), rule.getWindowSeconds());
                if (count < rule.getThresholdCount()) continue;
                Optional<IncidentEntity> active = incidents.findFirstByTenantIdAndFingerprintAndStatusIn(e.tenantId(), fp, List.of("OPEN", "ACKNOWLEDGED"));
                if (active.isPresent()) {
                    IncidentEntity incident = active.get();
                    incident.touch(e.timestamp());
                    continue;
                }
                // cooldown: do not reopen right after the same alert was resolved
                if (isCoolingDown(e.tenantId(), fp, rule)) continue;
                IncidentEntity incident = incidents.save(
                        new IncidentEntity(e.tenantId(), rule.getId(), fp,
                                rule.getName(),
                                "Threshold reached: " + count + " events in "
                                        + rule.getWindowSeconds() + " seconds",
                                e.timestamp(), count)
                );
                incidentsCreated.increment();
                String eventKey = "incident-created:" + incident.getTenantId() + ":" + incident.getId();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("eventId", eventKey);
                payload.put("tenantId", e.tenantId());
                payload.put("incidentId", incident.getId());
                payload.put("ruleId", rule.getId());
                payload.put("title", incident.getTitle());
                payload.put("status", incident.getStatus());
                payload.put("occurredAt", e.timestamp().toString());
                try {
                    outbox.save(new NotificationOutboxEntity(eventKey, e.tenantId(), mapper.writeValueAsString(payload), Instant.now()));
                } catch (com.fasterxml.jackson.core.JsonProcessingException jsonError) {
                    throw new IllegalStateException("Could not serialize incident notification", jsonError);
                }
            }
        } finally {
            sample.stop(evaluationTimer);
        }
    }

    /** True while "resolved_at + cooldownSeconds" of the latest resolved incident for this alert is still in the future. */
    private boolean isCoolingDown(String tenantId, String fingerprint, RuleEntity rule) {
        if (rule.getCooldownSeconds() <= 0) return false;
        return incidents
                .findFirstByTenantIdAndFingerprintAndStatusOrderByResolvedAtDesc(tenantId, fingerprint, "RESOLVED")
                .map(IncidentEntity::getResolvedAt)
                .filter(resolvedAt -> resolvedAt != null
                        && resolvedAt.plusSeconds(rule.getCooldownSeconds()).isAfter(Instant.now()))
                .isPresent();
    }

    private boolean matches(RuleEntity r, ProcessedLogEvent e) {
        return (r.getSeverity() == null || r.getSeverity().equalsIgnoreCase(e.severity().name())) && (r.getServiceName() == null || r.getServiceName().equals(e.service())) && (r.getEnvironment() == null || r.getEnvironment().equals(e.environment()));
    }

    private String fingerprint(ProcessedLogEvent e, RuleEntity r) {
        String raw = e.tenantId() + "|" + r.getId() + "|" + Objects.toString(e.service(), "") + "|" + Objects.toString(e.environment(), "") + "|" + e.severity();
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(b);
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
    }
}
