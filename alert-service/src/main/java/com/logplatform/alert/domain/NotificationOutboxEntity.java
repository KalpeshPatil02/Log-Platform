package com.logplatform.alert.domain;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="notification_outbox")
public class NotificationOutboxEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_key",nullable=false,unique=true) private String eventKey;
 @Column(name="tenant_id",nullable=false,length=36) private String tenantId;
 @Column(nullable=false,columnDefinition="json") private String payload;
 @Column(nullable=false) private String status="PENDING";
 @Column(nullable=false) private int attempts=0;
 @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="published_at") private Instant publishedAt;
 @Column(name="last_error",length=1000) private String lastError;
 protected NotificationOutboxEntity(){}
 public NotificationOutboxEntity(String key,String tenant,String payload,Instant now){eventKey=key;tenantId=tenant;this.payload=payload;createdAt=nextAttemptAt=now;}
 public Long getId(){return id;} public String getEventKey(){return eventKey;} public String getTenantId(){return tenantId;} public String getPayload(){return payload;} public int getAttempts(){return attempts;} public void published(Instant t){status="PUBLISHED";publishedAt=t;lastError=null;} public void failed(String e,Instant next){status="PENDING";attempts++;lastError=e==null?"unknown":e.substring(0,Math.min(1000,e.length()));nextAttemptAt=next;}
}
