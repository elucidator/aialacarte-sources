-- Deterministic seed data. Reset restores these exact rows.
-- Alice (1) and Bob (2) have no bookings.
-- Seed bookings do not create pending notifications.

INSERT INTO attendees (id, name) VALUES
    (1, 'Alice'),
    (2, 'Bob'),
    (100, 'Seed Attendee 01'),
    (101, 'Seed Attendee 02'),
    (102, 'Seed Attendee 03'),
    (103, 'Seed Attendee 04'),
    (104, 'Seed Attendee 05'),
    (105, 'Seed Attendee 06'),
    (106, 'Seed Attendee 07'),
    (107, 'Seed Attendee 08'),
    (108, 'Seed Attendee 09'),
    (109, 'Seed Attendee 10'),
    (110, 'Seed Attendee 11'),
    (111, 'Seed Attendee 12'),
    (112, 'Seed Attendee 13'),
    (113, 'Seed Attendee 14'),
    (114, 'Seed Attendee 15'),
    (115, 'Seed Attendee 16'),
    (116, 'Seed Attendee 17'),
    (117, 'Seed Attendee 18'),
    (118, 'Seed Attendee 19'),
    (119, 'Seed Attendee 20'),
    (120, 'Seed Attendee 21'),
    (121, 'Seed Attendee 22'),
    (122, 'Seed Attendee 23'),
    (123, 'Seed Attendee 24'),
    (124, 'Seed Attendee 25'),
    (125, 'Seed Attendee 26'),
    (126, 'Seed Attendee 27'),
    (127, 'Seed Attendee 28'),
    (128, 'Seed Attendee 29'),
    (129, 'Seed Attendee 30'),
    (130, 'Seed Attendee 31'),
    (131, 'Seed Attendee 32'),
    (132, 'Seed Attendee 33'),
    (133, 'Seed Attendee 34'),
    (134, 'Seed Attendee 35'),
    (135, 'Seed Attendee 36'),
    (136, 'Seed Attendee 37'),
    (137, 'Seed Attendee 38'),
    (138, 'Seed Attendee 39'),
    (139, 'Seed Attendee 40'),
    (140, 'Seed Attendee 41');

INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES
    (1, 'Debugging Distributed Systems', 'Room A', 30, 1),
    (2, 'Practical Kotlin', 'Room B', 20, 2),
    (3, 'Understanding Databases', 'Room C', 40, 3);

-- Session 1: 22 active bookings (attendees 100-121)
INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
SELECT g, 1, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
FROM generate_series(1, 22) AS g;

-- Session 2: 19 active bookings (attendees 100-118)
INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
SELECT 100 + g, 2, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
FROM generate_series(1, 19) AS g;

-- Session 3: 40 active bookings (attendees 100-139) — full
INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
SELECT 200 + g, 3, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
FROM generate_series(1, 40) AS g;

SELECT setval('bookings_id_seq', (SELECT MAX(id) FROM bookings));
