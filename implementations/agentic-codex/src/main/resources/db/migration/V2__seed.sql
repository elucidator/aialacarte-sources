INSERT INTO sessions VALUES
(1, 'Debugging Distributed Systems', 'Room A', 30),
(2, 'Practical Kotlin', 'Room B', 20),
(3, 'Understanding Databases', 'Room C', 40);
INSERT INTO bookings(id, session_id, attendee, status)
SELECT 'seed-' || s.id || '-' || n, s.id, 'fictional-' || s.id || '-' || n, 'ACTIVE'
FROM sessions s CROSS JOIN LATERAL generate_series(1, CASE s.id WHEN 1 THEN 22 WHEN 2 THEN 19 ELSE 40 END) n;
