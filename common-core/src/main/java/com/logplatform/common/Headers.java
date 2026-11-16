package com.logplatform.common;

/**
 * HTTP header names used between client, gateway and services.
 */
public final class Headers {
    private Headers() {
    }

    public static final String X_API_KEY = "X-API-KEY";
    public static final String X_CORRELATION_ID = "X-Correlation-Id";
    /**
     * Set by the gateway only. Any client-supplied value must be stripped.
     */
    public static final String X_TENANT_ID = "X-Tenant-Id";
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_ROLE = "X-User-Role";
}
