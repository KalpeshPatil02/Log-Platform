package com.logplatform.common;

/**
 * Redis Stream names and consumer groups. Single source of truth for all services.
 */
public final class Streams {
    private Streams() {
    }

    public static final String LOGS_RAW = "logs.raw";
    public static final String LOGS_PROCESSED = "logs.processed";
    public static final String LOGS_DLQ = "logs.dlq";
    public static final String NOTIFICATIONS_OUT = "notifications.out";
    public static final String GROUP_PROCESSING = "processing-service";
    public static final String GROUP_ALERTING = "alert-service";
    public static final String GROUP_NOTIFICATION = "notification-service";
}
