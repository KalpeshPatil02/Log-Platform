CREATE TABLE processed_logs (
                                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                tenant_id VARCHAR(100) NOT NULL,
                                event_id VARCHAR(150) NOT NULL,
                                api_key_id VARCHAR(100) NOT NULL,
                                received_at TIMESTAMP(6) NOT NULL,
                                occurred_at TIMESTAMP(6) NOT NULL,
                                severity VARCHAR(10) NOT NULL,
                                service_name VARCHAR(150) NOT NULL,
                                environment VARCHAR(50) NOT NULL,
                                message TEXT NOT NULL,
                                trace_id VARCHAR(128),
                                correlation_id VARCHAR(128),
                                attributes_json JSON,
                                CONSTRAINT uk_processed_tenant_event
                                    UNIQUE (tenant_id, event_id),
                                INDEX idx_processed_tenant_time (tenant_id, occurred_at),
                                INDEX idx_processed_tenant_level (tenant_id, severity)
) ENGINE=InnoDB;

CREATE TABLE outbox_events (
                               id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                               event_key VARCHAR(260) NOT NULL UNIQUE,
                               payload JSON NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               attempt_count INT NOT NULL DEFAULT 0,
                               next_attempt_at TIMESTAMP(6) NOT NULL,
                               created_at TIMESTAMP(6) NOT NULL,
                               published_at TIMESTAMP(6),
                               last_error VARCHAR(1000),
                               INDEX idx_outbox_status_created (status, next_attempt_at, id)
) ENGINE=InnoDB;