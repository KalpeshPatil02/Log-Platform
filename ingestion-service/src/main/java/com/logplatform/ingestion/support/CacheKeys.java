package com.logplatform.ingestion.support;

import com.logplatform.common.Hashing;

/** Redis cache key conventions local to ingestion-service. */
public final class CacheKeys {
    private CacheKeys() {}
    public static String apiKey(String sha256Hex) { return "ingestion:api-key:" + sha256Hex; }
    public static String dedup(String tenantId, String eventId) {
        return "ingestion:dedup:" + tenantId + ":" + Hashing.sha256Hex(eventId);
    }
}
