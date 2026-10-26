-- Baseline schema for Conference Planner
CREATE TABLE attendees (
    id          BIGINT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE
);

CREATE TABLE sessions (
    id          BIGINT PRIMARY KEY,
    title       TEXT NOT NULL,
    room        TEXT NOT NULL,
    capacity    INT  NOT NULL CHECK (capacity > 0),
    sort_order  INT  NOT NULL
);

CREATE TABLE bookings (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES sessions(id),
    attendee_id   BIGINT NOT NULL REFERENCES attendees(id),
    status        TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at  TIMESTAMPTZ
);

-- At most one ACTIVE booking per attendee per session
CREATE UNIQUE INDEX uq_active_booking_per_attendee_session
    ON bookings (session_id, attendee_id)
    WHERE status = 'ACTIVE';

-- Attendee-scoped idempotency keys for booking requests
CREATE TABLE booking_requests (
    attendee_id      BIGINT NOT NULL REFERENCES attendees(id),
    idempotency_key  TEXT NOT NULL,
    session_id       BIGINT NOT NULL REFERENCES sessions(id),
    booking_id       BIGINT NOT NULL REFERENCES bookings(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (attendee_id, idempotency_key)
);

-- Pending confirmation outbox (same TX as accepted booking)
CREATE TABLE confirmation_outbox (
    id             BIGSERIAL PRIMARY KEY,
    booking_id     BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    delivery_key   TEXT NOT NULL UNIQUE,
    status         TEXT NOT NULL CHECK (status IN ('PENDING', 'ACKNOWLEDGED')),
    attempt_count  INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fake notification gateway durable store (separate TX ownership)
CREATE TABLE gateway_messages (
    delivery_key   TEXT PRIMARY KEY,
    booking_id     BIGINT NOT NULL,
    payload        TEXT NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE gateway_acknowledgements (
    delivery_key   TEXT PRIMARY KEY REFERENCES gateway_messages(delivery_key),
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE delivery_attempts (
    id             BIGSERIAL PRIMARY KEY,
    booking_id     BIGINT NOT NULL REFERENCES bookings(id),
    delivery_key   TEXT NOT NULL,
    outcome        TEXT NOT NULL,
    detail         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE demo_activity (
    id             BIGSERIAL PRIMARY KEY,
    kind           TEXT NOT NULL,
    message        TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE demo_settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO demo_settings(key, value) VALUES ('simulate_notification_failure', 'false');
