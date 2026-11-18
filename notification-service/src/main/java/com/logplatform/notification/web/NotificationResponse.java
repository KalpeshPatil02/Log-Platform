package com.logplatform.notification.web;

import com.logplatform.notification.domain.NotificationDelivery;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String eventKey,
        String tenantId,
        String channel,
        String status,
        int attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant sentAt,
        String lastError,
        String destination,
        String payload
) {
    public static NotificationResponse from(NotificationDelivery d) {
        return new NotificationResponse(
                d.getId(),
                d.getEventKey(),
                d.getTenantId(),
                d.getChannel(),
                d.getStatus(),
                d.getAttempts(),
                d.getNextAttemptAt(),
                d.getCreatedAt(),
                d.getSentAt(),
                d.getLastError(),
                d.getDestination(),
                d.getPayload()
        );
    }
}
