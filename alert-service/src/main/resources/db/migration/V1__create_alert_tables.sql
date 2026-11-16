CREATE TABLE alert_rules (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 name VARCHAR(150) NOT NULL,
 description VARCHAR(500),
 severity VARCHAR(16),
 service_name VARCHAR(150),
 environment VARCHAR(80),
 threshold_count INT NOT NULL,
 window_seconds INT NOT NULL,
 cooldown_seconds INT NOT NULL DEFAULT 300,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 INDEX idx_rule_tenant_enabled (tenant_id, enabled),
 CONSTRAINT chk_rule_threshold CHECK (threshold_count > 0),
 CONSTRAINT chk_rule_window CHECK (window_seconds BETWEEN 1 AND 86400)
) ENGINE=InnoDB;

CREATE TABLE incidents (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 rule_id BIGINT NOT NULL,
 fingerprint VARCHAR(128) NOT NULL,
 status VARCHAR(24) NOT NULL,
 title VARCHAR(255) NOT NULL,
 description TEXT,
 first_seen_at TIMESTAMP(6) NOT NULL,
 last_seen_at TIMESTAMP(6) NOT NULL,
 occurrence_count BIGINT NOT NULL DEFAULT 1,
 acknowledged_by VARCHAR(150),
 assigned_to VARCHAR(150),
 resolved_at TIMESTAMP(6),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_incident_rule FOREIGN KEY (rule_id) REFERENCES alert_rules(id),
 INDEX idx_incident_tenant_status (tenant_id, status, last_seen_at),
 INDEX idx_incident_tenant_rule (tenant_id, rule_id),
 UNIQUE KEY uk_incident_active_fingerprint (tenant_id, fingerprint, status)
) ENGINE=InnoDB;

CREATE TABLE notification_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_key VARCHAR(220) NOT NULL,
 tenant_id BIGINT NOT NULL,
 payload JSON NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 published_at TIMESTAMP(6),
 last_error VARCHAR(1000),
 UNIQUE KEY uk_notification_outbox_event (event_key),
 INDEX idx_notification_outbox_pending (status, next_attempt_at, id)
) ENGINE=InnoDB;
