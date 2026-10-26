package dev.conferenceplanner.booking

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

/**
 * All booking SQL. Methods run in the caller's transaction (BookingService opens it); none of
 * them commit on their own.
 */
@Repository
class PostgresBookingRepository(private val jdbc: JdbcClient) {

    fun attendeeExists(attendeeId: String): Boolean =
        jdbc.sql("SELECT count(*) FROM attendees WHERE id = :id").param("id", attendeeId)
            .query(Int::class.javaObjectType).single() > 0

    /**
     * Claims the attendee-scoped request key. Returns false when a completed request with this key
     * already exists. A concurrent replay blocks here until the first transaction commits, so
     * concurrent replays of one key cannot both create a booking.
     */
    fun claimRequest(attendeeId: String, requestKey: String, sessionId: Long): Boolean =
        jdbc.sql(
            """
            INSERT INTO booking_requests (attendee_id, request_key, session_id)
            VALUES (:attendee, :key, :session)
            ON CONFLICT (attendee_id, request_key) DO NOTHING
            """,
        ).param("attendee", attendeeId).param("key", requestKey).param("session", sessionId).update() == 1

    fun findCompletedRequest(attendeeId: String, requestKey: String): CompletedRequest? =
        jdbc.sql("SELECT session_id, booking_id FROM booking_requests WHERE attendee_id = :attendee AND request_key = :key")
            .param("attendee", attendeeId).param("key", requestKey)
            .query { rs, _ -> CompletedRequest(rs.getLong("session_id"), rs.getLong("booking_id")) }
            .optional().orElse(null)

    fun completeRequest(attendeeId: String, requestKey: String, bookingId: Long) {
        jdbc.sql("UPDATE booking_requests SET booking_id = :booking WHERE attendee_id = :attendee AND request_key = :key")
            .param("booking", bookingId).param("attendee", attendeeId).param("key", requestKey).update()
    }

    /**
     * Invariant 1 coordination: the session row is the lock. Every booking for a session takes it,
     * so availability is counted and the booking inserted while no other booker can interleave.
     * Works across any number of application processes because PostgreSQL holds the lock.
     */
    fun lockSession(sessionId: Long): LockedSession? =
        jdbc.sql("SELECT id, title, capacity FROM sessions WHERE id = :id FOR UPDATE")
            .param("id", sessionId)
            .query { rs, _ -> LockedSession(rs.getLong("id"), rs.getString("title"), rs.getInt("capacity")) }
            .optional().orElse(null)

    fun countActive(sessionId: Long): Int =
        jdbc.sql("SELECT count(*) FROM bookings WHERE session_id = :session AND status = 'ACTIVE'")
            .param("session", sessionId).query(Int::class.javaObjectType).single()

    fun hasActiveBooking(sessionId: Long, attendeeId: String): Boolean =
        jdbc.sql("SELECT count(*) FROM bookings WHERE session_id = :session AND attendee_id = :attendee AND status = 'ACTIVE'")
            .param("session", sessionId).param("attendee", attendeeId)
            .query(Int::class.javaObjectType).single() > 0

    fun insertBooking(sessionId: Long, attendeeId: String): Long =
        jdbc.sql("INSERT INTO bookings (session_id, attendee_id, status) VALUES (:session, :attendee, 'ACTIVE') RETURNING id")
            .param("session", sessionId).param("attendee", attendeeId)
            .query(Long::class.javaObjectType).single()

    fun findBooking(bookingId: Long): Booking? =
        jdbc.sql("$SELECT_BOOKING WHERE b.id = :id").param("id", bookingId)
            .query { rs, _ -> booking(rs) }.optional().orElse(null)

    /** Locks the booking row so repeated cancellations cannot release a place twice. */
    fun lockBookingOwnedBy(bookingId: Long, attendeeId: String): Booking? =
        jdbc.sql("$SELECT_BOOKING WHERE b.id = :id AND b.attendee_id = :attendee FOR UPDATE OF b")
            .param("id", bookingId).param("attendee", attendeeId)
            .query { rs, _ -> booking(rs) }.optional().orElse(null)

    fun cancel(bookingId: Long) {
        jdbc.sql("UPDATE bookings SET status = 'CANCELLED', cancelled_at = now() WHERE id = :id AND status = 'ACTIVE'")
            .param("id", bookingId).update()
    }

    fun listActive(attendeeId: String): List<Booking> =
        jdbc.sql("$SELECT_BOOKING WHERE b.attendee_id = :attendee AND b.status = 'ACTIVE' ORDER BY b.id")
            .param("attendee", attendeeId).query { rs, _ -> booking(rs) }.list()

    private fun booking(rs: ResultSet) = Booking(
        id = rs.getLong("id"),
        sessionId = rs.getLong("session_id"),
        sessionTitle = rs.getString("title"),
        attendeeId = rs.getString("attendee_id"),
        status = BookingStatus.valueOf(rs.getString("status")),
        createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        cancelledAt = rs.getObject("cancelled_at", OffsetDateTime::class.java)?.toInstant(),
    )

    private companion object {
        const val SELECT_BOOKING = """
            SELECT b.id, b.session_id, s.title, b.attendee_id, b.status, b.created_at, b.cancelled_at
            FROM bookings b JOIN sessions s ON s.id = b.session_id
        """
    }
}
