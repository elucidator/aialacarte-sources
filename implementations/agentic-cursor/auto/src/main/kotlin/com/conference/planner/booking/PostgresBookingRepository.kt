package com.conference.planner.booking

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Statement

/**
 * Concrete PostgreSQL repository used by BookingService in the baseline.
 * Extracting an interface from this type is exercise 3.
 */
@Repository
class PostgresBookingRepository(
    private val jdbc: JdbcTemplate,
) {
    fun countSessions(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM sessions", Long::class.java)!!

    fun listSessions(limit: Int, offset: Int): List<SessionRow> =
        jdbc.query(
            """
            SELECT s.id, s.title, s.room, s.capacity,
                   (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') AS active_bookings
              FROM sessions s
             ORDER BY s.sort_order ASC, s.id ASC
             LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                val capacity = rs.getInt("capacity")
                val active = rs.getInt("active_bookings")
                SessionRow(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = capacity,
                    activeBookings = active,
                )
            },
            limit,
            offset,
        )

    /**
     * Lock the session for booking coordination across app processes.
     * Uses a transaction-scoped advisory lock keyed by session id, then locks the
     * session row and re-reads the active booking count inside that critical section.
     */
    fun findSessionForUpdate(sessionId: Long): SessionRow? {
        // void-returning lock; ignore the null result column
        jdbc.query(
            "SELECT pg_advisory_xact_lock(?)",
            { _, _ -> null },
            sessionId,
        )

        val session = jdbc.query(
            """
            SELECT id, title, room, capacity
              FROM sessions
             WHERE id = ?
             FOR UPDATE
            """.trimIndent(),
            { rs, _ ->
                SessionRow(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = rs.getInt("capacity"),
                    activeBookings = 0,
                )
            },
            sessionId,
        ).firstOrNull() ?: return null

        val active = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM bookings
             WHERE session_id = ? AND status = 'ACTIVE'
            """.trimIndent(),
            Int::class.java,
            sessionId,
        )!!

        return session.copy(activeBookings = active)
    }

    fun findSession(sessionId: Long): SessionRow? =
        jdbc.query(
            """
            SELECT s.id, s.title, s.room, s.capacity,
                   (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') AS active_bookings
              FROM sessions s
             WHERE s.id = ?
            """.trimIndent(),
            { rs, _ ->
                SessionRow(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = rs.getInt("capacity"),
                    activeBookings = rs.getInt("active_bookings"),
                )
            },
            sessionId,
        ).firstOrNull()

    fun attendeeExists(attendeeId: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM attendees WHERE id = ?)",
            Boolean::class.java,
            attendeeId,
        )!!

    fun findBookingRequest(attendeeId: Long, idempotencyKey: String): BookingRequestRow? =
        jdbc.query(
            """
            SELECT attendee_id, idempotency_key, session_id, booking_id
              FROM booking_requests
             WHERE attendee_id = ? AND idempotency_key = ?
            """.trimIndent(),
            { rs, _ ->
                BookingRequestRow(
                    attendeeId = rs.getLong("attendee_id"),
                    idempotencyKey = rs.getString("idempotency_key"),
                    sessionId = rs.getLong("session_id"),
                    bookingId = rs.getLong("booking_id"),
                )
            },
            attendeeId,
            idempotencyKey,
        ).firstOrNull()

    fun hasActiveBooking(sessionId: Long, attendeeId: Long): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM bookings
               WHERE session_id = ? AND attendee_id = ? AND status = 'ACTIVE'
            )
            """.trimIndent(),
            Boolean::class.java,
            sessionId,
            attendeeId,
        )!!

    fun insertBooking(sessionId: Long, attendeeId: Long): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbc.update({ connection ->
            val ps = connection.prepareStatement(
                """
                INSERT INTO bookings (session_id, attendee_id, status)
                VALUES (?, ?, 'ACTIVE')
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, sessionId)
            ps.setLong(2, attendeeId)
            ps
        }, keyHolder)
        return (keyHolder.keys?.get("id") as? Number)?.toLong()
            ?: keyHolder.key!!.toLong()
    }

    fun insertBookingRequest(
        attendeeId: Long,
        idempotencyKey: String,
        sessionId: Long,
        bookingId: Long,
    ) {
        jdbc.update(
            """
            INSERT INTO booking_requests (attendee_id, idempotency_key, session_id, booking_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            attendeeId,
            idempotencyKey,
            sessionId,
            bookingId,
        )
    }

    fun insertConfirmationOutbox(bookingId: Long, deliveryKey: String) {
        jdbc.update(
            """
            INSERT INTO confirmation_outbox (booking_id, delivery_key, status)
            VALUES (?, ?, 'PENDING')
            """.trimIndent(),
            bookingId,
            deliveryKey,
        )
    }

    fun findBooking(bookingId: Long): BookingRow? =
        jdbc.query(
            """
            SELECT b.id, b.session_id, s.title AS session_title,
                   b.attendee_id, a.name AS attendee_name, b.status
              FROM bookings b
              JOIN sessions s ON s.id = b.session_id
              JOIN attendees a ON a.id = b.attendee_id
             WHERE b.id = ?
            """.trimIndent(),
            { rs, _ -> mapBooking(rs) },
            bookingId,
        ).firstOrNull()

    fun listActiveBookings(attendeeId: Long): List<BookingRow> =
        jdbc.query(
            """
            SELECT b.id, b.session_id, s.title AS session_title,
                   b.attendee_id, a.name AS attendee_name, b.status
              FROM bookings b
              JOIN sessions s ON s.id = b.session_id
              JOIN attendees a ON a.id = b.attendee_id
             WHERE b.attendee_id = ? AND b.status = 'ACTIVE'
             ORDER BY b.id ASC
            """.trimIndent(),
            { rs, _ -> mapBooking(rs) },
            attendeeId,
        )

    /**
     * Cancel an owned active booking. Returns true if a row transitioned to CANCELLED.
     * A second call on an already-cancelled owned booking returns false but is treated as success
     * by the service (idempotent 204).
     */
    fun cancelActiveBooking(bookingId: Long, attendeeId: Long): Int =
        jdbc.update(
            """
            UPDATE bookings
               SET status = 'CANCELLED', cancelled_at = NOW()
             WHERE id = ? AND attendee_id = ? AND status = 'ACTIVE'
            """.trimIndent(),
            bookingId,
            attendeeId,
        )

    fun bookingOwnedBy(bookingId: Long, attendeeId: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM bookings WHERE id = ? AND attendee_id = ?)",
            Boolean::class.java,
            bookingId,
            attendeeId,
        )!!

    private fun mapBooking(rs: java.sql.ResultSet): BookingRow =
        BookingRow(
            id = rs.getLong("id"),
            sessionId = rs.getLong("session_id"),
            sessionTitle = rs.getString("session_title"),
            attendeeId = rs.getLong("attendee_id"),
            attendeeName = rs.getString("attendee_name"),
            status = rs.getString("status"),
        )
}
