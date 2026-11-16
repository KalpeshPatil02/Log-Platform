package com.logplatform.processing.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="app.processing")
public record ProcessingProperties(String rawStream, String processedStream, String deadLetterStream,
 String group, String consumerName, int batchSize, long pendingIdleMillis) {}
