package com.logplatform.ingestion.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        String identityUrl,
        String internalToken,
        int maxStreamLength,
        Duration dedupTtl,
        Cache cache,
        boolean requireEventId
) {
    @ConstructorBinding
    public IngestionProperties {
    }

    public IngestionProperties(
            String identityUrl,
            String internalToken,
            int maxStreamLength,
            Duration dedupTtl,
            Cache cache
    ) {
        this(identityUrl, internalToken, maxStreamLength, dedupTtl, cache, false);
    }

    public record Cache(Duration validTtl, Duration invalidTtl) {
    }
}