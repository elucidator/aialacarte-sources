package com.conferenceplanner.repository

import com.conferenceplanner.model.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class BookingRepository(private val jdbc: JdbcTemplate) {

    private val sessionViewMapper = RowMapper<SessionView> { rs: ResultSet, _ ->
        SessionView(
            id = UUID.fromString(rs.getString("id")),
            title = rs.getString("title"),
            room = rs.getString("room"),
            capacity = rs.getInt("capacity"),
            activeBookings = rs.getInt("active_bookings"),
            availablePlaces = rs.getInt("available_places")
        )
    }

    private val bookingMapper = RowMapper<Booking> { rs: ResultSet, _ ->
        Booking(
            id = UUID.fromString(rs.getString("id")),
            sessionId = UUID.fromString(rs.getString("session_id")),
            attendee = rs.getString("attendee"),
            idempotencyKey = rs.getString("idempotency_key"),
            status = BookingStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun findSessionsWithCounts(page: Int, size: Int): PagedResult<SessionView> {
        val total = jdbc.queryForObject("SELECT count(*) FROM sessions", Long::class.java)!!
        val items = jdbc.query(
            """
            SELECT s.id, s.title, s.room, s.capacity,
                   coalesce(b.cnt, 0) AS active_bookings,
                   s.capacity - coalesce(b.cnt, 0) AS available_places
            FROM sessions s
            LEFT JOIN (
                SELECT session_id, count(*) AS cnt
                FROM bookings WHERE status = 'ACTIVE'
                GROUP BY session_id
            ) b ON s.id = b.session_id
            ORDER BY s.sort_order
            LIMIT ? OFFSET ?
            """,
            sessionViewMapper, size, page * size
        )
        return PagedResult(items, page, size, total)
    }

    fun sessionExists(sessionId: UUID): Boolean {
        return jdbc.queryForObject(
            "SELECT count(*) FROM sessions WHERE id = ?",
            Int::class.java, sessionId
        )!! > 0
    }

    fun findSessionForUpdate(sessionId: UUID): Session? {
        val results = jdbc.query(
            "SELECT id, title, room, capacity, sort_order FROM sessions WHERE id = ? FOR UPDATE",
            { rs: ResultSet, _ ->
                Session(
                    id = UUID.fromString(rs.getString("id")),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = rs.getInt("capacity"),
                    sortOrder = rs.getInt("sort_order")
                )
            }, sessionId
        )
        return results.firstOrNull()
    }

    fun getSessionCapacity(sessionId: UUID): Int {
        return jdbc.queryForObject(
            "SELECT capacity FROM sessions WHERE id = ?",
            Int::class.java, sessionId
        )!!
    }

    fun countActiveBookings(sessionId: UUID): Int {
        return jdbc.queryForObject(
            "SELECT count(*) FROM bookings WHERE session_id = ? AND status = 'ACTIVE'",
            Int::class.java, sessionId
        )!!
    }

    fun findByIdempotencyKey(key: String): Booking? {
        val results = jdbc.query(
            "SELECT * FROM bookings WHERE idempotency_key = ?",
            bookingMapper, key
        )
        return results.firstOrNull()
    }

    fun findActiveByAttendeeAndSession(attendee: String, sessionId: UUID): Booking? {
        val results = jdbc.query(
            "SELECT * FROM bookings WHERE attendee = ? AND session_id = ? AND status = 'ACTIVE'",
            bookingMapper, attendee, sessionId
        )
        return results.firstOrNull()
    }

    fun insertBookingAndNotification(
        bookingId: UUID,
        sessionId: UUID,
        attendee: String,
        idempotencyKey: String,
        deliveryKey: String
    ): Booking {
        val now = Instant.now()
        jdbc.update(
            """
            INSERT INTO bookings (id, session_id, attendee, idempotency_key, status, created_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?)
            """,
            bookingId, sessionId, attendee, idempotencyKey,
            java.sql.Timestamp.from(now)
        )
        val notificationId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO notifications (id, booking_id, delivery_key, status, attempts, created_at)
            VALUES (?, ?, ?, 'PENDING', 0, ?)
            """,
            notificationId, bookingId, deliveryKey,
            java.sql.Timestamp.from(now)
        )
        return Booking(bookingId, sessionId, attendee, idempotencyKey, BookingStatus.ACTIVE, now)
    }

    fun findActiveByAttendee(attendee: String): List<Booking> {
        return jdbc.query(
            """
            SELECT b.* FROM bookings b
            JOIN sessions s ON b.session_id = s.id
            WHERE b.attendee = ? AND b.status = 'ACTIVE'
            ORDER BY s.sort_order
            """,
            bookingMapper, attendee
        )
    }

    fun findById(id: UUID): Booking? {
        val results = jdbc.query(
            "SELECT * FROM bookings WHERE id = ?",
            bookingMapper, id
        )
        return results.firstOrNull()
    }

    fun cancelBooking(id: UUID) {
        jdbc.update(
            "UPDATE bookings SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE'",
            id
        )
    }

    fun findSessionTitle(sessionId: UUID): String? {
        val results = jdbc.queryForList(
            "SELECT title FROM sessions WHERE id = ?",
            String::class.java, sessionId
        )
        return results.firstOrNull()
    }

    fun findPendingNotifications(): List<Notification> {
        return jdbc.query(
            """
            SELECT * FROM notifications
            WHERE status = 'PENDING' AND attempts < 3
            ORDER BY created_at
            """,
            notificationMapper
        )
    }

    fun incrementNotificationAttempt(id: UUID) {
        jdbc.update(
            "UPDATE notifications SET attempts = attempts + 1, last_attempt_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now()), id
        )
    }

    fun markNotificationDelivered(id: UUID) {
        jdbc.update(
            "UPDATE notifications SET status = 'DELIVERED' WHERE id = ?",
            id
        )
    }

    fun findGatewayDeliveryByKey(deliveryKey: String): GatewayDelivery? {
        val results = jdbc.query(
            "SELECT * FROM gateway_deliveries WHERE delivery_key = ?",
            gatewayDeliveryMapper, deliveryKey
        )
        return results.firstOrNull()
    }

    fun insertGatewayDelivery(id: UUID, deliveryKey: String, payload: String): GatewayDelivery {
        val now = Instant.now()
        jdbc.update(
            """
            INSERT INTO gateway_deliveries (id, delivery_key, payload, accepted, acknowledged, created_at)
            VALUES (?, ?, ?, true, false, ?)
            """,
            id, deliveryKey, payload, java.sql.Timestamp.from(now)
        )
        return GatewayDelivery(id, deliveryKey, payload, true, false, now)
    }

    fun acknowledgeGatewayDelivery(id: UUID) {
        jdbc.update(
            "UPDATE gateway_deliveries SET acknowledged = true WHERE id = ?",
            id
        )
    }

    fun findAllNotificationsForActivity(): List<Map<String, Any?>> {
        return jdbc.queryForList(
            """
            SELECT n.id, n.booking_id, n.delivery_key, n.status, n.attempts, n.last_attempt_at, n.created_at,
                   b.attendee, b.session_id, s.title AS session_title
            FROM notifications n
            JOIN bookings b ON n.booking_id = b.id
            JOIN sessions s ON b.session_id = s.id
            ORDER BY n.created_at DESC
            """
        )
    }

    fun findAllGatewayDeliveries(): List<GatewayDelivery> {
        return jdbc.query(
            "SELECT * FROM gateway_deliveries ORDER BY created_at DESC",
            gatewayDeliveryMapper
        )
    }

    private val notificationMapper = RowMapper<Notification> { rs: ResultSet, _ ->
        Notification(
            id = UUID.fromString(rs.getString("id")),
            bookingId = UUID.fromString(rs.getString("booking_id")),
            deliveryKey = rs.getString("delivery_key"),
            status = NotificationStatus.valueOf(rs.getString("status")),
            attempts = rs.getInt("attempts"),
            lastAttemptAt = rs.getTimestamp("last_attempt_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val gatewayDeliveryMapper = RowMapper<GatewayDelivery> { rs: ResultSet, _ ->
        GatewayDelivery(
            id = UUID.fromString(rs.getString("id")),
            deliveryKey = rs.getString("delivery_key"),
            payload = rs.getString("payload"),
            accepted = rs.getBoolean("accepted"),
            acknowledged = rs.getBoolean("acknowledged"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
