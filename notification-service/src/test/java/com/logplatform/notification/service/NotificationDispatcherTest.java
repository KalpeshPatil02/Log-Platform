package com.logplatform.notification.service;

import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.domain.NotificationDelivery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static NotificationProperties props(String smtpPassword, String slackWebhookUrl) {
        return new NotificationProperties(
            "notifications.out", "notification-service", "notification-1",
            1000L, 60000L, 5, 1000L, 3600000L,
            true, true, true,
            "smtp.gmail.com", 587, "user@test.com", smtpPassword, slackWebhookUrl, "https://example.com/webhook"
        );
    }

    @Test
    void privateIpBlocked() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "WEBHOOK", "{}", "http://192.168.1.1/webhook", null);
        assertThrows(IllegalArgumentException.class, () -> d.send(del));
    }

    @Test
    void metadataIpBlocked() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "WEBHOOK", "{}", "http://169.254.169.254/metadata", null);
        assertThrows(IllegalArgumentException.class, () -> d.send(del));
    }

    @Test
    void localhostBlocked() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "WEBHOOK", "{}", "http://localhost/webhook", null);
        assertThrows(IllegalArgumentException.class, () -> d.send(del));
    }

    @Test
    void publicUrlAllowed() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.postForObject(any(URI.class), any(HttpEntity.class), ArgumentMatchers.eq(String.class)))
            .thenReturn("ok");

        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null,
            new RestTemplateBuilder() {
                @Override
                public RestTemplate build() { return rest; }
            }, registry);

        NotificationDelivery del = new NotificationDelivery("k1", "t1", "WEBHOOK", "{\"msg\":\"hi\"}", "http://1.2.3.4/webhook", null);
        assertDoesNotThrow(() -> d.send(del));
        verify(rest, times(1)).postForObject(any(URI.class), any(HttpEntity.class), ArgumentMatchers.eq(String.class));
    }

    @Test
    void unknownChannelThrows() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "UNKNOWN", "{}", null, null);
        assertThrows(IllegalArgumentException.class, () -> d.send(del));
    }

    @Test
    void emailNotConfiguredThrows() {
        NotificationDispatcher d = new DispatcherTestImpl(props(null, "http://slack.example.com"), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "EMAIL", "{}", "user@test.com", null);
        assertThrows(IllegalStateException.class, () -> d.send(del));
    }

    @Test
    void slackNotConfiguredThrows() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", null), null, new RestTemplateBuilder(), registry);
        NotificationDelivery del = new NotificationDelivery("k1", "t1", "SLACK", "{}", null, null);
        assertThrows(IllegalStateException.class, () -> d.send(del));
    }

    @Test
    void validateSSRFDirectly() {
        NotificationDispatcher d = new DispatcherTestImpl(props("pw", "http://slack.example.com"), null, new RestTemplateBuilder(), registry);

        // Blocked IPs
        assertThrows(IllegalArgumentException.class, () -> d.validateSSRF(URI.create("http://10.0.0.1/x")));
        assertThrows(IllegalArgumentException.class, () -> d.validateSSRF(URI.create("http://172.16.0.1/x")));
        assertThrows(IllegalArgumentException.class, () -> d.validateSSRF(URI.create("http://192.168.1.1/x")));
        assertThrows(IllegalArgumentException.class, () -> d.validateSSRF(URI.create("http://127.0.0.1/x")));
        assertThrows(IllegalArgumentException.class, () -> d.validateSSRF(URI.create("http://169.254.169.254/x")));

        // Allowed public IPs (may fail on DNS in sandbox, but pattern check should pass)
        assertDoesNotThrow(() -> d.validateSSRF(URI.create("http://8.8.8.8/x")));
        assertDoesNotThrow(() -> d.validateSSRF(URI.create("http://1.1.1.1/x")));
    }

    /** Test subclass exposing package-private validateSSRF via send() */
    static class DispatcherTestImpl extends NotificationDispatcher {
        DispatcherTestImpl(NotificationProperties p, org.springframework.mail.javamail.JavaMailSender m, RestTemplateBuilder b, SimpleMeterRegistry r) {
            super(p, m, b, r);
        }
    }
}
