package com.logplatform.processing.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

 private static final int MAX_ATTEMPTS = 20;

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 @Column(name = "event_key", nullable = false, unique = true, length = 260)
 private String eventKey;

 @Column(nullable = false, columnDefinition = "json")
 private String payload;

 @Column(nullable = false, length = 20)
 private String status = "PENDING";

 @Column(name = "attempt_count", nullable = false)
 private int attemptCount = 0;

 @Column(name = "next_attempt_at", nullable = false)
 private Instant nextAttemptAt;

 @Column(name = "created_at", nullable = false)
 private Instant createdAt;

 @Column(name = "published_at")
 private Instant publishedAt;

 @Column(name = "last_error", length = 1000)
 private String lastError;

 protected OutboxEventEntity() {
 }

 public OutboxEventEntity(
         String eventKey,
         String payload,
         Instant now) {
  this.eventKey = eventKey;
  this.payload = payload;
  this.createdAt = now;
  this.nextAttemptAt = now;
 }

 public Long getId() {
  return id;
 }

 public String getEventKey() {
  return eventKey;
 }

 public String getPayload() {
  return payload;
 }

 public int getAttemptCount() {
  return attemptCount;
 }

 public String getStatus() {
  return status;
 }

 public void published(Instant now) {
  this.status = "PUBLISHED";
  this.publishedAt = now;
  this.lastError = null;
 }

 public void failed(String error, Instant nextAttempt) {
  this.attemptCount++;

  this.lastError = error == null
          ? "Unknown publishing error"
          : error.substring(0, Math.min(1000, error.length()));

  if (this.attemptCount >= MAX_ATTEMPTS) {
   this.status = "FAILED";
  } else {
   this.status = "PENDING";
   this.nextAttemptAt = nextAttempt;
  }
 }
}