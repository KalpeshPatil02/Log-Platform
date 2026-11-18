package com.logplatform.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_delivery", uniqueConstraints = {
    @UniqueConstraint(name = "uk_event_tenant", columnNames = {"event_key", "tenant_id"})
})
public class NotificationDelivery {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "destination", length = 500)
    private String destination;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Version
    private long version;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(String eventKey, String tenantId, String channel, String payload, String destination, Instant now) {
        this.eventKey = eventKey;
        this.tenantId = tenantId;
        this.channel = channel;
        this.payload = payload;
        this.destination = destination;
        this.status = PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String v) { eventKey = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { channel = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { attempts = v; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant v) { nextAttemptAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { sentAt = v; }
    public String getLastError() { return lastError; }
    public void setLastError(String v) { lastError = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { destination = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { payload = v; }
}
