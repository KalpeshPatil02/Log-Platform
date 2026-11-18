package com.logplatform.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
    String stream,
    String consumerGroup,
    String consumerName,
    @DefaultValue("1000") long pollDelayMs,
    @DefaultValue("60000") long pendingIdleMs,
    @DefaultValue("5") int maxDeliveries,
    @DefaultValue("1000") long backoffBaseMs,
    @DefaultValue("3600000") long backoffMaxMs,
    @DefaultValue("true") boolean emailEnabled,
    @DefaultValue("true") boolean slackEnabled,
    @DefaultValue("true") boolean webhookEnabled,
    @DefaultValue("smtp.gmail.com") String smtpHost,
    @DefaultValue("587") int smtpPort,
    String smtpUsername,
    String smtpPassword,
    String slackWebhookUrl,
    String webhookBaseUrl
) {
}
