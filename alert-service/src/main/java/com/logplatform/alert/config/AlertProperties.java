package com.logplatform.alert.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="app.alert")
public record AlertProperties(String processedStream, String deadLetterStream, String consumerGroup,
 String consumerName, long pollDelayMs, String windowKeyPrefix, long cooldownSeconds, String notificationStream) { }
