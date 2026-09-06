CREATE TABLE system_accounts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    counselor_id INT UNIQUE,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_counselor FOREIGN KEY (counselor_id) REFERENCES counselors(id),
    CONSTRAINT chk_account_role CHECK (role IN ('ADMIN', 'VIEWER')),
    CONSTRAINT chk_account_version CHECK (version >= 0)
);

-- Serializes account administration, including the last-enabled-administrator check.
CREATE TABLE account_admin_guard (id INT PRIMARY KEY);
INSERT INTO account_admin_guard VALUES (1);

CREATE TABLE audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64),
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);
CREATE INDEX idx_audit_occurred ON audit_events(occurred_at, id);
