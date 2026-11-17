package com.logplatform.alert.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties(prefix="app.alert")
public record AlertProperties(String processedStream, String deadLetterStream, String consumerGroup,
 String consumerName, long pollDelayMs, String windowKeyPrefix, long cooldownSeconds, String notificationStream,
 @DefaultValue("60000") long pendingIdleMs, @DefaultValue("5") int maxDeliveries) { }
