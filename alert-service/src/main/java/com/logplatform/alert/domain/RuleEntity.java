package com.logplatform.alert.domain;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="alert_rules")
public class RuleEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="tenant_id",nullable=false) private Long tenantId;
 @Column(nullable=false,length=150) private String name;
 @Column(length=500) private String description;
 @Column(length=16) private String severity;
 @Column(name="service_name",length=150) private String serviceName;
 @Column(length=80) private String environment;
 @Column(name="threshold_count",nullable=false) private int thresholdCount;
 @Column(name="window_seconds",nullable=false) private int windowSeconds;
 @Column(name="cooldown_seconds",nullable=false) private int cooldownSeconds=300;
 @Column(nullable=false) private boolean enabled=true;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void create(){createdAt=updatedAt=Instant.now();} @PreUpdate void update(){updatedAt=Instant.now();}
 public Long getId(){return id;} public Long getTenantId(){return tenantId;} public void setTenantId(Long v){tenantId=v;}
 public String getName(){return name;} public void setName(String v){name=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;} public String getServiceName(){return serviceName;} public void setServiceName(String v){serviceName=v;}
 public String getEnvironment(){return environment;} public void setEnvironment(String v){environment=v;} public int getThresholdCount(){return thresholdCount;} public void setThresholdCount(int v){thresholdCount=v;}
 public int getWindowSeconds(){return windowSeconds;} public void setWindowSeconds(int v){windowSeconds=v;} public int getCooldownSeconds(){return cooldownSeconds;} public void setCooldownSeconds(int v){cooldownSeconds=v;}
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
}
