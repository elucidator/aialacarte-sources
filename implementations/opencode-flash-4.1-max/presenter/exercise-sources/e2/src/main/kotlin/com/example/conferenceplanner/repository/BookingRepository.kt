package com.example.conferenceplanner.repository

import com.example.conferenceplanner.model.BookingView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

/**
 * Concrete PostgreSQL repository used by the booking flow.
 *
 * All statements participate in the transaction opened by BookingService (JdbcTemplate
 * is bound to the thread's Spring-managed connection).
 */
@Repository
class BookingRepository(private val jdbc: JdbcTemplate) {

    data class SessionRow(val id: Long, val title: String, val room: String, val capacity: Int)
    data class IdempotencyRow(val bookingId: Long?, val payloadHash: String)

    private val bookingMapper = RowMapper { rs, _ ->
        BookingView(
            id = rs.getLong("id"),
            sessionId = rs.getLong("session_id"),
            sessionTitle = rs.getString("session_title"),
            sessionRoom = rs.getString("session_room"),
            attendee = rs.getString("attendee"),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            cancelledAt = rs.getTimestamp("cancelled_at")?.toInstant(),
        )
    }

    fun findSession(sessionId: Long): SessionRow? =
        jdbc.query(
            "select id, title, room, capacity from sessions where id = ?",
            { rs, _ -> SessionRow(rs.getLong("id"), rs.getString("title"), rs.getString("room"), rs.getInt("capacity")) },
            sessionId,
        ).firstOrNull()

    fun countActiveBookings(sessionId: Long): Int =
        jdbc.queryForObject(
            "select count(*) from bookings where session_id = ? and status = 'ACTIVE'",
            Int::class.java,
            sessionId,
        ) ?: 0

    fun findActiveBookingId(sessionId: Long, attendee: String): Long? =
        jdbc.query(
            "select id from bookings where session_id = ? and attendee = ? and status = 'ACTIVE'",
            { rs, _ -> rs.getLong("id") },
            sessionId, attendee,
        ).firstOrNull()

    fun insertBooking(sessionId: Long, attendee: String): Long =
        jdbc.queryForObject(
            "insert into bookings (session_id, attendee, status) values (?, ?, 'ACTIVE') returning id",
            Long::class.java,
            sessionId, attendee,
        )!!

    fun insertNotification(bookingId: Long, deliveryKey: String) {
        jdbc.update(
            "insert into notifications (delivery_key, booking_id, status) values (?, ?, 'PENDING')",
            deliveryKey, bookingId,
        )
    }

    fun insertIdempotencyKey(attendee: String, requestKey: String, payloadHash: String): Boolean =
        jdbc.update(
            """
            insert into idempotency_keys (attendee, request_key, payload_hash)
            values (?, ?, ?)
            on conflict (attendee, request_key) do nothing
            """.trimIndent(),
            attendee, requestKey, payloadHash,
        ) == 1

    fun findIdempotencyKey(attendee: String, requestKey: String): IdempotencyRow? =
        jdbc.query(
            "select booking_id, payload_hash from idempotency_keys where attendee = ? and request_key = ?",
            { rs, _ -> IdempotencyRow(rs.getLong("booking_id").takeIf { !rs.wasNull() }, rs.getString("payload_hash")) },
            attendee, requestKey,
        ).firstOrNull()

    fun attachIdempotencyBooking(attendee: String, requestKey: String, bookingId: Long) {
        jdbc.update(
            "update idempotency_keys set booking_id = ? where attendee = ? and request_key = ?",
            bookingId, attendee, requestKey,
        )
    }

    fun findBooking(bookingId: Long): BookingView? = findBooking(bookingId, forUpdate = false)

    fun findBookingForUpdate(bookingId: Long): BookingView? = findBooking(bookingId, forUpdate = true)

    private fun findBooking(bookingId: Long, forUpdate: Boolean): BookingView? {
        val lock = if (forUpdate) " for update of b" else ""
        return jdbc.query(
            """
            select b.id, b.session_id, b.attendee, b.status, b.created_at, b.cancelled_at,
                   s.title as session_title, s.room as session_room
            from bookings b
            join sessions s on s.id = b.session_id
            where b.id = ?$lock
            """.trimIndent(),
            bookingMapper,
            bookingId,
        ).firstOrNull()
    }

    fun cancelBooking(bookingId: Long) {
        jdbc.update(
            "update bookings set status = 'CANCELLED', cancelled_at = now() where id = ?",
            bookingId,
        )
    }

    fun listActiveBookings(attendee: String): List<BookingView> =
        jdbc.query(
            """
            select b.id, b.session_id, b.attendee, b.status, b.created_at, b.cancelled_at,
                   s.title as session_title, s.room as session_room
            from bookings b
            join sessions s on s.id = b.session_id
            where b.attendee = ? and b.status = 'ACTIVE'
            order by b.id desc
            """.trimIndent(),
            bookingMapper,
            attendee,
        )

    fun addActivity(kind: String, message: String) {
        jdbc.update("insert into demo_activity (kind, message) values (?, ?)", kind, message)
    }
}
