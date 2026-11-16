CREATE TABLE tenants (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(63)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_tenants_slug UNIQUE (slug)
);

CREATE TABLE users (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(36)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE user_roles (
    user_id VARCHAR(36) NOT NULL,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE api_keys (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    key_prefix    VARCHAR(16)  NOT NULL,
    key_hash      CHAR(64)     NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_by    VARCHAR(36)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NULL,
    last_used_at  DATETIME(6)  NULL,
    revoked_at    DATETIME(6)  NULL,
    CONSTRAINT uk_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_api_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    INDEX idx_api_keys_tenant (tenant_id)
);

CREATE TABLE audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id      VARCHAR(36)  NULL,
    actor_id       VARCHAR(36)  NULL,
    action         VARCHAR(60)  NOT NULL,
    resource_type  VARCHAR(40)  NULL,
    resource_id    VARCHAR(64)  NULL,
    details        VARCHAR(1000) NULL,
    ip_address     VARCHAR(45)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    INDEX idx_audit_tenant_created (tenant_id, created_at)
);
