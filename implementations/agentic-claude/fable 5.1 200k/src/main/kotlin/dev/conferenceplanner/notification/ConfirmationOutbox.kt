package dev.conferenceplanner.notification

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class PendingConfirmation(
    val bookingId: Long,
    val deliveryKey: String,
    val attempts: Int,
    val attendeeId: String,
    val sessionTitle: String,
)

enum class AttemptOutcome { ACKNOWLEDGED, DUPLICATE_ACKNOWLEDGED, TIMEOUT }

/** Pending confirmations and their delivery attempts. Runs in the caller's transaction. */
@Repository
class ConfirmationOutbox(private val jdbc: JdbcClient) {

    fun enqueue(bookingId: Long, deliveryKey: String) {
        jdbc.sql("INSERT INTO confirmations (booking_id, delivery_key, status) VALUES (:booking, :key, 'PENDING')")
            .param("booking", bookingId).param("key", deliveryKey).update()
    }

    fun pending(limit: Int, maxAttempts: Int): List<PendingConfirmation> =
        jdbc.sql(
            """
            SELECT c.booking_id, c.delivery_key, c.attempts, b.attendee_id, s.title
            FROM confirmations c
            JOIN bookings b ON b.id = c.booking_id
            JOIN sessions s ON s.id = b.session_id
            WHERE c.status = 'PENDING' AND c.attempts < :maxAttempts
            ORDER BY c.booking_id
            LIMIT :limit
            """,
        ).param("maxAttempts", maxAttempts).param("limit", limit)
            .query { rs, _ ->
                PendingConfirmation(rs.getLong("booking_id"), rs.getString("delivery_key"), rs.getInt("attempts"), rs.getString("attendee_id"), rs.getString("title"))
            }.list()

    fun recordAttempt(bookingId: Long, attemptNo: Int, deliveryKey: String, outcome: AttemptOutcome) {
        jdbc.sql("INSERT INTO delivery_attempts (booking_id, attempt_no, delivery_key, outcome) VALUES (:booking, :no, :key, :outcome)")
            .param("booking", bookingId).param("no", attemptNo).param("key", deliveryKey).param("outcome", outcome.name).update()
        jdbc.sql("UPDATE confirmations SET attempts = attempts + 1 WHERE booking_id = :booking").param("booking", bookingId).update()
    }

    fun markDelivered(bookingId: Long) {
        jdbc.sql("UPDATE confirmations SET status = 'DELIVERED', delivered_at = now() WHERE booking_id = :booking")
            .param("booking", bookingId).update()
    }
}
