ALTER TABLE departments ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE departments ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE counselors (
    id INT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    department_id INT NOT NULL,
    employment_status VARCHAR(16) NOT NULL,
    photo_path VARCHAR(255),
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_counselor_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT chk_employment_status CHECK (employment_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_counselor_version CHECK (version >= 0)
);
CREATE INDEX idx_counselors_department_status ON counselors(department_id, employment_status, id);
CREATE INDEX idx_counselors_status ON counselors(employment_status, id);

CREATE TABLE counselor_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    counselor_id INT NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_history_counselor FOREIGN KEY (counselor_id) REFERENCES counselors(id)
);
CREATE INDEX idx_status_history_counselor ON counselor_status_history(counselor_id, id);

-- Populate this table explicitly before V3 when migrating a legacy database.
CREATE TABLE legacy_counselor_mapping (
    legacy_user_id INT PRIMARY KEY,
    employee_no VARCHAR(32) NOT NULL UNIQUE,
    CONSTRAINT fk_mapping_legacy_user FOREIGN KEY (legacy_user_id) REFERENCES users(id)
);
