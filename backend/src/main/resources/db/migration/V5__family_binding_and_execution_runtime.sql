CREATE TABLE trip_family_role_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    active_device_id VARCHAR(64),
    binding_version BIGINT NOT NULL DEFAULT 0,
    bound_at TIMESTAMP NULL,
    CONSTRAINT fk_family_role_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT uq_family_role UNIQUE (trip_id, role),
    CONSTRAINT uq_family_active_device UNIQUE (trip_id, active_device_id)
);

CREATE TABLE trip_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    device_token_hash CHAR(64) NOT NULL,
    binding_version BIGINT NOT NULL,
    bound_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    CONSTRAINT fk_trip_device_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT uq_trip_device UNIQUE (trip_id, device_id),
    CONSTRAINT uq_device_token_hash UNIQUE (device_token_hash)
);

CREATE TABLE trip_device_binding_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    role VARCHAR(32) NOT NULL,
    new_device_id VARCHAR(64) NOT NULL,
    replaced_device_id VARCHAR(64),
    binding_version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_binding_action_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT uq_binding_request UNIQUE (trip_id, request_id)
);

CREATE TABLE trip_execution (
    trip_id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    actual_start_date DATE NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_execution_trip FOREIGN KEY (trip_id) REFERENCES trip(id)
);

CREATE TABLE trip_stop_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    stop_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by_role VARCHAR(32) NOT NULL,
    updated_by_device_id VARCHAR(64) NOT NULL,
    CONSTRAINT fk_stop_execution_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT fk_stop_execution_stop FOREIGN KEY (stop_id) REFERENCES trip_stop(id),
    CONSTRAINT uq_stop_execution UNIQUE (trip_id, stop_id)
);

CREATE TABLE trip_execution_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    stop_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL,
    applied_revision BIGINT NOT NULL,
    CONSTRAINT fk_execution_action_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT fk_execution_action_stop FOREIGN KEY (stop_id) REFERENCES trip_stop(id),
    CONSTRAINT uq_execution_request UNIQUE (trip_id, request_id)
);

INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'FATHER', '爸爸', 0 FROM trip WHERE code = 'qinggan-2026-family';
INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'MOTHER', '妈妈', 0 FROM trip WHERE code = 'qinggan-2026-family';
INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'OLDER_SISTER', '姐姐', 0 FROM trip WHERE code = 'qinggan-2026-family';
INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'YOUNGER_BROTHER', '弟弟', 0 FROM trip WHERE code = 'qinggan-2026-family';
INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'GRANDFATHER', '爷爷', 0 FROM trip WHERE code = 'qinggan-2026-family';
INSERT INTO trip_family_role_binding (trip_id, role, display_name, binding_version)
SELECT id, 'GRANDMOTHER', '奶奶', 0 FROM trip WHERE code = 'qinggan-2026-family';

INSERT INTO trip_execution (trip_id, status, actual_start_date, revision, updated_at)
SELECT id, status, actual_start_date, 0, CURRENT_TIMESTAMP
FROM trip
WHERE code = 'qinggan-2026-family';
