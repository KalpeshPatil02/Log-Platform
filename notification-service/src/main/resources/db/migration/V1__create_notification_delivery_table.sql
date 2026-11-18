CREATE TABLE IF NOT EXISTS notification_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    last_error TEXT,
    destination VARCHAR(500),
    payload TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_event_tenant UNIQUE (event_key, tenant_id),
    INDEX idx_status_next_attempt (status, next_attempt_at),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
