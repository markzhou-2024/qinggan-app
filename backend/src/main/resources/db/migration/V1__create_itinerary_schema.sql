CREATE TABLE trip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_days INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE place (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    place_type VARCHAR(32) NOT NULL,
    province VARCHAR(64),
    city VARCHAR(64),
    latitude_wgs84 DECIMAL(10,7),
    longitude_wgs84 DECIMAL(10,7),
    latitude_gcj02 DECIMAL(10,7),
    longitude_gcj02 DECIMAL(10,7),
    priority VARCHAR(16),
    recommended_duration_minutes INT,
    description TEXT
);

CREATE TABLE trip_day (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    day_number INT NOT NULL,
    date DATE NOT NULL,
    title VARCHAR(255) NOT NULL,
    day_type VARCHAR(32) NOT NULL,
    planned_distance_km INT,
    planned_distance_display VARCHAR(64),
    planned_drive_minutes INT,
    planned_drive_display VARCHAR(64),
    overnight_place_id BIGINT,
    sequence INT NOT NULL,
    CONSTRAINT fk_trip_day_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT fk_trip_day_overnight_place FOREIGN KEY (overnight_place_id) REFERENCES place(id),
    CONSTRAINT uq_trip_day_number UNIQUE (trip_id, day_number),
    CONSTRAINT uq_trip_day_date UNIQUE (trip_id, date),
    CONSTRAINT uq_trip_day_sequence UNIQUE (trip_id, sequence)
);

CREATE TABLE trip_stop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_day_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    sequence INT NOT NULL,
    stop_type VARCHAR(32) NOT NULL,
    priority VARCHAR(16),
    planned_arrival_time TIME,
    planned_departure_time TIME,
    planned_duration_minutes INT,
    optional BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_trip_stop_day FOREIGN KEY (trip_day_id) REFERENCES trip_day(id),
    CONSTRAINT fk_trip_stop_place FOREIGN KEY (place_id) REFERENCES place(id),
    CONSTRAINT uq_trip_stop_sequence UNIQUE (trip_day_id, sequence)
);

CREATE TABLE navigation_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(255),
    navigation_type VARCHAR(32) NOT NULL,
    navigation_keyword VARCHAR(255),
    recommended BOOLEAN NOT NULL,
    warning_text VARCHAR(500),
    verification_status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_navigation_point_place FOREIGN KEY (place_id) REFERENCES place(id)
);

CREATE TABLE navigation_point_coordinate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    navigation_point_id BIGINT NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    coordinate_system VARCHAR(16) NOT NULL,
    primary_coordinate BOOLEAN NOT NULL,
    CONSTRAINT fk_navigation_coordinate_point FOREIGN KEY (navigation_point_id) REFERENCES navigation_point(id),
    CONSTRAINT uq_navigation_coordinate_system UNIQUE (navigation_point_id, coordinate_system)
);

CREATE TABLE stay (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_day_id BIGINT NOT NULL UNIQUE,
    hotel_name VARCHAR(128) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(64),
    check_in_note VARCHAR(500),
    parking_note VARCHAR(500),
    verification_status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_stay_day FOREIGN KEY (trip_day_id) REFERENCES trip_day(id)
);

CREATE TABLE stay_coordinate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stay_id BIGINT NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    coordinate_system VARCHAR(16) NOT NULL,
    primary_coordinate BOOLEAN NOT NULL,
    CONSTRAINT fk_stay_coordinate_stay FOREIGN KEY (stay_id) REFERENCES stay(id),
    CONSTRAINT uq_stay_coordinate_system UNIQUE (stay_id, coordinate_system)
);

CREATE TABLE trip_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL UNIQUE,
    current_day_number INT NOT NULL,
    current_stop_id BIGINT,
    last_completed_stop_id BIGINT,
    state VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_progress_trip FOREIGN KEY (trip_id) REFERENCES trip(id),
    CONSTRAINT fk_progress_current_stop FOREIGN KEY (current_stop_id) REFERENCES trip_stop(id),
    CONSTRAINT fk_progress_completed_stop FOREIGN KEY (last_completed_stop_id) REFERENCES trip_stop(id)
);
