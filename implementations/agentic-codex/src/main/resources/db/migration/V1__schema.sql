CREATE TABLE sessions (
    id BIGINT PRIMARY KEY,
    title TEXT NOT NULL,
    room TEXT NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0)
);
CREATE TABLE bookings (
    id TEXT PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sessions(id),
    attendee TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX one_active_booking ON bookings(attendee, session_id) WHERE status = 'ACTIVE';
CREATE TABLE booking_requests (
    attendee TEXT NOT NULL,
    request_key TEXT NOT NULL,
    session_id BIGINT NOT NULL,
    booking_id TEXT NOT NULL REFERENCES bookings(id),
    PRIMARY KEY (attendee, request_key)
);
CREATE TABLE confirmations (
    booking_id TEXT PRIMARY KEY REFERENCES bookings(id),
    delivery_key TEXT NOT NULL UNIQUE,
    acknowledged BOOLEAN NOT NULL DEFAULT false,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE gateway_messages (
    delivery_key TEXT PRIMARY KEY,
    booking_id TEXT NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE gateway_attempts (
    id BIGSERIAL PRIMARY KEY,
    delivery_key TEXT NOT NULL,
    booking_id TEXT NOT NULL,
    outcome TEXT NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE activity (
    id BIGSERIAL PRIMARY KEY,
    kind TEXT NOT NULL,
    booking_id TEXT,
    detail TEXT NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE demo_settings (id INTEGER PRIMARY KEY CHECK (id=1), mode TEXT NOT NULL);
INSERT INTO demo_settings VALUES (1, 'normal');
