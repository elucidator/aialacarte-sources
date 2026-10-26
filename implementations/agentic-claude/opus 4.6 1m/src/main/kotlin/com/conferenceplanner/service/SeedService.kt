package com.conferenceplanner.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class SeedService(
    private val jdbc: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate
) {

    companion object {
        val SESSION_1_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val SESSION_2_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val SESSION_3_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    }

    fun resetToSeed() {
        transactionTemplate.execute { _ ->
            jdbc.update("DELETE FROM gateway_deliveries")
            jdbc.update("DELETE FROM notifications")
            jdbc.update("DELETE FROM bookings")
            jdbc.update("DELETE FROM sessions")

            val now = Timestamp.from(Instant.now())

            jdbc.update(
                "INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES (?, ?, ?, ?, ?)",
                SESSION_1_ID, "Debugging Distributed Systems", "Room A", 30, 1
            )
            jdbc.update(
                "INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES (?, ?, ?, ?, ?)",
                SESSION_2_ID, "Practical Kotlin", "Room B", 20, 2
            )
            jdbc.update(
                "INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES (?, ?, ?, ?, ?)",
                SESSION_3_ID, "Understanding Databases", "Room C", 40, 3
            )

            seedBookings(SESSION_1_ID, 22, "session1", now)
            seedBookings(SESSION_2_ID, 19, "session2", now)
            seedBookings(SESSION_3_ID, 40, "session3", now)
        }
    }

    private fun seedBookings(sessionId: UUID, count: Int, prefix: String, now: Timestamp) {
        for (i in 1..count) {
            val bookingId = UUID.nameUUIDFromBytes("$prefix-booking-$i".toByteArray())
            val attendee = "seed-attendee-$prefix-$i"
            val key = "seed-key-$prefix-$i"
            jdbc.update(
                """
                INSERT INTO bookings (id, session_id, attendee, idempotency_key, status, created_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """,
                bookingId, sessionId, attendee, key, now
            )
        }
    }
}
