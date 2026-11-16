package com.logplatform.ingestion.security;

/** Who is calling, as decided by identity-service. The ONLY source of tenant id for ingested logs. */
public record ApiKeyPrincipal(String tenantId, String apiKeyId) {}
