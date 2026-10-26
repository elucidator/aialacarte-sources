package com.example.conferenceplanner.repository

import com.example.conferenceplanner.model.ActivityEntry
import com.example.conferenceplanner.service.SeedData
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class DemoRepository(private val jdbc: JdbcTemplate) {

    /**
     * Clears all demo state and restores the deterministic seed.
     *
     * Table locks serialise the reset against in-flight booking/cancellation
     * transactions running in this or any other application process.
     */
    fun resetToSeed() {
        lockForReset()
        clearAll()
        insertSeed()
        // Constant defined in code, not user input; utility statements cannot take bind parameters.
        jdbc.execute("alter table bookings alter column id restart with ${SeedData.NEXT_BOOKING_ID}")
        jdbc.update(
            "insert into demo_controls (name, value) values (?, 'false')",
            SeedData.FAILURE_SIMULATION_CONTROL,
        )
    }

    /** Seeds an empty database exactly once; returns true when seeding happened. */
    fun seedIfEmpty(): Boolean {
        lockForReset()
        val sessions = jdbc.queryForObject("select count(*) from sessions", Long::class.java) ?: 0L
        if (sessions > 0) return false
        clearAll()
        insertSeed()
        jdbc.execute("alter table bookings alter column id restart with ${SeedData.NEXT_BOOKING_ID}")
        jdbc.update(
            "insert into demo_controls (name, value) values (?, 'false') on conflict (name) do nothing",
            SeedData.FAILURE_SIMULATION_CONTROL,
        )
        return true
    }

    private fun lockForReset() {
        jdbc.execute("lock table sessions in exclusive mode")
        jdbc.execute("lock table bookings in exclusive mode")
    }

    private fun clearAll() {
        jdbc.execute("delete from delivery_attempts")
        jdbc.execute("delete from gateway_messages")
        jdbc.execute("delete from notifications")
        jdbc.execute("delete from idempotency_keys")
        jdbc.execute("delete from demo_activity")
        jdbc.execute("delete from bookings")
        jdbc.execute("delete from sessions")
        jdbc.execute("delete from demo_controls")
    }

    private fun insertSeed() {
        for (session in SeedData.sessions) {
            jdbc.update(
                "insert into sessions (id, title, room, capacity) values (?, ?, ?, ?)",
                session.id, session.title, session.room, session.capacity,
            )
        }
        val seededAt = Timestamp.from(java.time.Instant.parse("2026-01-01T09:00:00Z"))
        for (booking in SeedData.seedBookings) {
            jdbc.update(
                "insert into bookings (id, session_id, attendee, status, created_at) values (?, ?, ?, 'ACTIVE', ?)",
                booking.id, booking.sessionId, booking.attendee, seededAt,
            )
        }
    }

    fun listActivity(limit: Int): List<ActivityEntry> =
        jdbc.query(
            "select id, kind, message, created_at from demo_activity order by id desc limit ?",
            { rs, _ ->
                ActivityEntry(
                    id = rs.getLong("id"),
                    kind = rs.getString("kind"),
                    message = rs.getString("message"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
            limit,
        )

    fun controls(): Map<String, String> =
        jdbc.query("select name, value from demo_controls order by name") { rs, _ ->
            rs.getString("name") to rs.getString("value")
        }.toMap()

    fun setControl(name: String, value: String) {
        jdbc.update(
            """
            insert into demo_controls (name, value) values (?, ?)
            on conflict (name) do update set value = excluded.value
            """.trimIndent(),
            name, value,
        )
    }

    fun countBookings(): Long =
        jdbc.queryForObject("select count(*) from bookings", Long::class.java) ?: 0L

    fun countActivity(): Long =
        jdbc.queryForObject("select count(*) from demo_activity", Long::class.java) ?: 0L
}
