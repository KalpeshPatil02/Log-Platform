package com.logplatform.alert.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 @Column(name = "tenant_id", nullable = false, length = 36)
 private String tenantId;

 @Column(name = "rule_id", nullable = false)
 private Long ruleId;

 @Column(nullable = false, length = 128)
 private String fingerprint;

 /** Equals fingerprint while OPEN/ACKNOWLEDGED, NULL once RESOLVED. Unique per tenant, so a fingerprint can be resolved many times. */
 @Column(name = "active_fingerprint", length = 128)
 private String activeFingerprint;

 @Column(nullable = false, length = 24)
 private String status;

 @Column(nullable = false, length = 255)
 private String title;

 @Column(columnDefinition = "TEXT")
 private String description;

 @Column(name = "first_seen_at", nullable = false)
 private Instant firstSeenAt;

 @Column(name = "last_seen_at", nullable = false)
 private Instant lastSeenAt;

 @Column(name = "occurrence_count", nullable = false)
 private long occurrenceCount = 1;

 @Column(name = "acknowledged_by")
 private String acknowledgedBy;

 @Column(name = "assigned_to")
 private String assignedTo;

 @Column(name = "resolved_at")
 private Instant resolvedAt;

 @Version
 private long version;

 protected IncidentEntity() {
 }

 public IncidentEntity(
         String tenantId,
         long ruleId,
         String fingerprint,
         String title,
         String description,
         Instant now
 ) {
  this(tenantId, ruleId, fingerprint, title, description, now, 1);
 }

 public IncidentEntity(
         String tenantId,
         long ruleId,
         String fingerprint,
         String title,
         String description,
         Instant now,
         long occurrenceCount
 ) {
  this.tenantId = tenantId;
  this.ruleId = ruleId;
  this.fingerprint = fingerprint;
  this.activeFingerprint = fingerprint;
  this.title = title;
  this.description = description;
  this.status = "OPEN";
  this.firstSeenAt = now;
  this.lastSeenAt = now;
  this.occurrenceCount = occurrenceCount;
 }

 public Long getId() { return id; }
 public String getTenantId() { return tenantId; }
 public Long getRuleId() { return ruleId; }
 public String getFingerprint() { return fingerprint; }
 public String getActiveFingerprint() { return activeFingerprint; }
 public String getStatus() { return status; }
 public String getTitle() { return title; }
 public String getDescription() { return description; }
 public Instant getFirstSeenAt() { return firstSeenAt; }
 public Instant getLastSeenAt() { return lastSeenAt; }
 public long getOccurrenceCount() { return occurrenceCount; }
 public String getAcknowledgedBy() { return acknowledgedBy; }
 public String getAssignedTo() { return assignedTo; }
 public Instant getResolvedAt() { return resolvedAt; }

 public void touch(Instant t) {
  lastSeenAt = t;
  occurrenceCount++;
 }

 public void acknowledge(String who) {
  if (!status.equals("OPEN")) {
   throw new IllegalStateException(
           "Only OPEN incidents can be acknowledged"
   );
  }
  status = "ACKNOWLEDGED";
  acknowledgedBy = who;
 }

 public void assign(String who) {
  if (status.equals("RESOLVED")) {
   throw new IllegalStateException(
           "Resolved incident cannot be assigned"
   );
  }
  assignedTo = who;
 }

 public void resolve(Instant t) {
  if (status.equals("RESOLVED")) return;
  status = "RESOLVED";
  activeFingerprint = null;
  resolvedAt = t;
 }
}