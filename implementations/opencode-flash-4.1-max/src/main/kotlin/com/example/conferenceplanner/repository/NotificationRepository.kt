package com.example.conferenceplanner.repository

import com.example.conferenceplanner.model.NotificationView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class NotificationRepository(private val jdbc: JdbcTemplate) {

    data class PendingNotification(val deliveryKey: String, val bookingId: Long, val attempts: Int)

    private val viewMapper = RowMapper { rs, _ ->
        NotificationView(
            deliveryKey = rs.getString("delivery_key"),
            bookingId = rs.getLong("booking_id"),
            sessionTitle = rs.getString("session_title"),
            attendee = rs.getString("attendee"),
            status = rs.getString("status"),
            attempts = rs.getInt("attempts"),
            acceptedDeliveries = rs.getInt("accepted_deliveries"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            acknowledgedAt = rs.getTimestamp("acknowledged_at")?.toInstant(),
        )
    }

    fun listPending(limit: Int): List<PendingNotification> =
        jdbc.query(
            """
            select delivery_key, booking_id, attempts
            from notifications
            where status = 'PENDING'
            order by created_at asc, delivery_key asc
            limit ?
            """.trimIndent(),
            { rs, _ -> PendingNotification(rs.getString("delivery_key"), rs.getLong("booking_id"), rs.getInt("attempts")) },
            limit,
        )

    fun listForPanel(limit: Int): List<NotificationView> =
        jdbc.query(
            """
            select n.delivery_key, n.booking_id, n.status, n.attempts, n.created_at, n.acknowledged_at,
                   b.attendee,
                   s.title as session_title,
                   (select count(*) from gateway_messages g where g.booking_id = n.booking_id) as accepted_deliveries
            from notifications n
            join bookings b on b.id = n.booking_id
            join sessions s on s.id = b.session_id
            order by n.booking_id desc
            limit ?
            """.trimIndent(),
            viewMapper,
            limit,
        )

    fun incrementAttempts(deliveryKey: String): Int =
        jdbc.queryForObject(
            "update notifications set attempts = attempts + 1 where delivery_key = ? returning attempts",
            Int::class.java,
            deliveryKey,
        ) ?: 0

    fun insertAttempt(deliveryKey: String, attemptNumber: Int, outcome: String, detail: String) {
        jdbc.update(
            "insert into delivery_attempts (delivery_key, attempt_number, outcome, detail) values (?, ?, ?, ?)",
            deliveryKey, attemptNumber, outcome, detail,
        )
    }

    fun markAcknowledged(deliveryKey: String) {
        jdbc.update(
            "update notifications set status = 'ACKNOWLEDGED', acknowledged_at = ? where delivery_key = ?",
            java.sql.Timestamp.from(Instant.now()), deliveryKey,
        )
    }

    fun countPending(): Long =
        jdbc.queryForObject("select count(*) from notifications where status = 'PENDING'", Long::class.java) ?: 0L

    fun countAcknowledged(): Long =
        jdbc.queryForObject("select count(*) from notifications where status = 'ACKNOWLEDGED'", Long::class.java) ?: 0L
}
