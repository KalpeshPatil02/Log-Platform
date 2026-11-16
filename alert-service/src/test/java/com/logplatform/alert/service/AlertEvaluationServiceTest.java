package com.logplatform.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.alert.config.AlertProperties;
import com.logplatform.alert.domain.IncidentEntity;
import com.logplatform.alert.domain.RuleEntity;
import com.logplatform.alert.repository.IncidentRepository;
import com.logplatform.alert.repository.NotificationOutboxRepository;
import com.logplatform.alert.repository.RuleRepository;
import com.logplatform.common.ProcessedLogEvent;
import com.logplatform.common.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertEvaluationServiceTest {

    private RuleRepository rules;
    private IncidentRepository incidents;
    private NotificationOutboxRepository outbox;
    private SlidingWindowCounter counter;
    private StringRedisTemplate redis;
    private AlertEvaluationService evaluator;

    private final Instant eventTime = Instant.parse("2026-11-14T04:00:00Z");

    @BeforeEach
    void setUp() {
        rules = mock(RuleRepository.class);
        incidents = mock(IncidentRepository.class);
        outbox = mock(NotificationOutboxRepository.class);
        counter = mock(SlidingWindowCounter.class);
        redis = mock(StringRedisTemplate.class);

        AlertProperties properties = new AlertProperties(
                "logs.processed",
                "logs.dlq",
                "alert-service",
                "alert-test",
                1000L,
                "alert:window:",
                300L,
                "notifications.out"
        );

        evaluator = new AlertEvaluationService(
                rules, incidents, outbox, counter, redis, new ObjectMapper(), properties
        );
    }

    @Test
    void nullEventIsIgnored() {
        evaluator.evaluate(null);

        verifyNoInteractions(rules, incidents, outbox, counter);
    }

    @Test
    void eventWithInvalidTenantIsIgnored() {
        ProcessedLogEvent event = event(0L);

        evaluator.evaluate(event);

        verifyNoInteractions(rules, incidents, outbox, counter);
    }

    @Test
    void belowThresholdDoesNotCreateIncidentOrOutbox() {
        RuleEntity rule = matchingRule();
        when(rules.findByTenantIdAndEnabledTrue(42L)).thenReturn(List.of(rule));
        when(counter.addAndCount(anyString(), anyString(), any(Instant.class), anyInt())).thenReturn(4L);

        evaluator.evaluate(event(42L));

        verify(counter).addAndCount(anyString(), eq("event-1"), eq(eventTime), eq(60));
        verify(incidents, never()).save(any(IncidentEntity.class));
        verify(outbox, never()).save(any());
    }

    @Test
    void reachingThresholdCreatesIncidentAndNotificationOutbox() {
        RuleEntity rule = matchingRule();
        when(rules.findByTenantIdAndEnabledTrue(42L)).thenReturn(List.of(rule));
        when(counter.addAndCount(anyString(), anyString(), any(Instant.class), anyInt())).thenReturn(5L);
        when(incidents.findFirstByTenantIdAndFingerprintAndStatusIn(
                eq(42L), anyString(), anyList()
        )).thenReturn(Optional.empty());
        when(incidents.save(any(IncidentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        evaluator.evaluate(event(42L));

        verify(incidents).save(argThat(incident ->
                incident.getTenantId().equals(42L)
                        && incident.getRuleId().equals(11L)
                        && incident.getStatus().equals("OPEN")
                        && incident.getTitle().equals("Payment API errors")
        ));
        verify(outbox).save(argThat(notification ->
                notification.getTenantId() == 42L
                        && notification.getEventKey().startsWith("incident-created:42:")
                        && notification.getPayload().contains("\"incidentId\"")
        ));
    }

    @Test
    void matchingActiveIncidentIsTouchedInsteadOfCreatingAnotherIncident() {
        RuleEntity rule = matchingRule();
        IncidentEntity active = new IncidentEntity(
                42L, 11L, "fingerprint", "Payment API errors", "existing incident", eventTime
        );

        when(rules.findByTenantIdAndEnabledTrue(42L)).thenReturn(List.of(rule));
        when(counter.addAndCount(anyString(), anyString(), any(Instant.class), anyInt())).thenReturn(5L);
        when(incidents.findFirstByTenantIdAndFingerprintAndStatusIn(
                eq(42L), anyString(), anyList()
        )).thenReturn(Optional.of(active));

        evaluator.evaluate(event(42L));

        assertEquals(2L, active.getOccurrenceCount());
        assertEquals(eventTime, active.getLastSeenAt());
        verify(incidents, never()).save(any(IncidentEntity.class));
        verify(outbox, never()).save(any());
    }

    @Test
    void nonMatchingRuleDoesNotIncrementCounter() {
        RuleEntity rule = matchingRule();
        rule.setServiceName("different-service");
        when(rules.findByTenantIdAndEnabledTrue(42L)).thenReturn(List.of(rule));

        evaluator.evaluate(event(42L));

        verifyNoInteractions(counter);
        verify(incidents, never()).save(any(IncidentEntity.class));
    }

    private RuleEntity matchingRule() {
        RuleEntity rule = new RuleEntity();
        ReflectionTestUtils.setField(rule, "id", 11L);
        rule.setTenantId(42L);
        rule.setName("Payment API errors");
        rule.setSeverity("ERROR");
        rule.setServiceName("payment-api");
        rule.setEnvironment("production");
        rule.setThresholdCount(5);
        rule.setWindowSeconds(60);
        rule.setCooldownSeconds(300);
        rule.setEnabled(true);
        return rule;
    }

    private ProcessedLogEvent event(long tenantId) {
        return new ProcessedLogEvent(
                "event-1",
                tenantId,
                eventTime,
                "payment-api",
                "production",
                Severity.ERROR,
                "Payment failed",
                "trace-1",
                "source-fingerprint"
        );
    }
}
