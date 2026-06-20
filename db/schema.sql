CREATE DATABASE healthcare_db;

\c healthcare_db;

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    phone           VARCHAR(50)  NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'PATIENT' CHECK (role IN ('PATIENT', 'ADMIN')),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS slots (
    id              BIGSERIAL PRIMARY KEY,
    doctor_name     VARCHAR(255) NOT NULL,
    specialization  VARCHAR(255) NOT NULL,
    slot_date       DATE         NOT NULL,
    start_time      TIME         NOT NULL,
    end_time        TIME         NOT NULL,
    is_booked       BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (doctor_name, slot_date, start_time)
);

CREATE INDEX IF NOT EXISTS idx_slots_available ON slots (slot_date, is_booked);

CREATE TABLE IF NOT EXISTS appointments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    slot_id         BIGINT NOT NULL UNIQUE REFERENCES slots(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'BOOKED', 'NOTIFIED', 'CANCELLED', 'COMPLETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appointments_user ON appointments (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_appointment_per_slot
    ON appointments (slot_id)
    WHERE status IN ('PENDING', 'BOOKED', 'NOTIFIED');

CREATE TABLE IF NOT EXISTS appointment_event_logs (
    id                BIGSERIAL PRIMARY KEY,
    appointment_id    BIGINT NOT NULL REFERENCES appointments(id),
    event_type        VARCHAR(30) NOT NULL CHECK (event_type IN ('APPOINTMENT_CREATED', 'APPOINTMENT_CANCELLED')),
    event_status      VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
                          CHECK (event_status IN ('PUBLISHED', 'PROCESSING', 'PROCESSED', 'FAILED')),
    payload           TEXT,
    processing_note   TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_logs_appointment ON appointment_event_logs (appointment_id, created_at DESC);

CREATE TABLE IF NOT EXISTS processed_events (
    id              BIGSERIAL   PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'CLAIMED'
                        CHECK (status IN ('CLAIMED', 'PROCESSED', 'FAILED', 'DLQ')),
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    last_retry_at   TIMESTAMP,
    failure_reason  TEXT,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_event_id ON processed_events (event_id);
CREATE INDEX IF NOT EXISTS idx_processed_events_status   ON processed_events (status);

CREATE TABLE IF NOT EXISTS dlq_events (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL,
    appointment_id  BIGINT,
    event_type      VARCHAR(30),
    original_payload TEXT,
    failure_reason  TEXT         NOT NULL,
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    dlq_topic       VARCHAR(255) NOT NULL,
    published_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dlq_events_appointment ON dlq_events (appointment_id);
CREATE INDEX IF NOT EXISTS idx_dlq_events_published   ON dlq_events (published_at DESC);

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_id    BIGINT NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL DEFAULT 'Appointment',
    event_type      VARCHAR(50) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    published_at    TIMESTAMP,
    failure_reason  TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events (status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate      ON outbox_events (aggregate_id, aggregate_type);
