CREATE TABLE sessions (
    id         UUID PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    room       VARCHAR(100) NOT NULL,
    capacity   INT NOT NULL CHECK (capacity > 0),
    sort_order INT NOT NULL
);

CREATE TABLE bookings (
    id              UUID PRIMARY KEY,
    session_id      UUID NOT NULL REFERENCES sessions(id),
    attendee        VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Prevent duplicate active bookings per attendee/session
CREATE UNIQUE INDEX uq_active_booking_attendee_session
    ON bookings (attendee, session_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_bookings_session_status ON bookings (session_id, status);
CREATE INDEX idx_bookings_attendee_status ON bookings (attendee, status);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    delivery_key    VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_status ON notifications (status);

CREATE TABLE gateway_deliveries (
    id           UUID PRIMARY KEY,
    delivery_key VARCHAR(255) NOT NULL UNIQUE,
    payload      TEXT NOT NULL,
    accepted     BOOLEAN NOT NULL DEFAULT true,
    acknowledged BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
