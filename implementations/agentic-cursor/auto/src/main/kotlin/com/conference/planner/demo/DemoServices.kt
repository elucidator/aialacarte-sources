package com.conference.planner.demo

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Repository
class DemoSettingsRepository(
    private val jdbc: JdbcTemplate,
) {
    fun isSimulateNotificationFailure(): Boolean =
        jdbc.queryForObject(
            "SELECT value FROM demo_settings WHERE key = 'simulate_notification_failure'",
            String::class.java,
        ) == "true"

    fun setSimulateNotificationFailure(enabled: Boolean) {
        jdbc.update(
            """
            INSERT INTO demo_settings(key, value) VALUES ('simulate_notification_failure', ?)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
            """.trimIndent(),
            if (enabled) "true" else "false",
        )
    }
}

@Service
class DemoActivityService(
    private val jdbc: JdbcTemplate,
) {
    fun record(kind: String, message: String) {
        jdbc.update(
            "INSERT INTO demo_activity (kind, message) VALUES (?, ?)",
            kind,
            message,
        )
    }

    fun recent(limit: Int = 50): List<ActivityRow> =
        jdbc.query(
            """
            SELECT id, kind, message, created_at
              FROM demo_activity
             ORDER BY id DESC
             LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                ActivityRow(
                    id = rs.getLong("id"),
                    kind = rs.getString("kind"),
                    message = rs.getString("message"),
                    createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                )
            },
            limit,
        )
}

data class ActivityRow(
    val id: Long,
    val kind: String,
    val message: String,
    val createdAt: String,
)

/**
 * Restores deterministic seed sessions/bookings and clears demo request state.
 * Seed bookings do not create pending notifications.
 */
@Service
class SeedResetService(
    private val jdbc: JdbcTemplate,
    private val demoActivity: DemoActivityService,
) {
    @Transactional
    fun reset() {
        jdbc.update("DELETE FROM gateway_acknowledgements")
        jdbc.update("DELETE FROM gateway_messages")
        jdbc.update("DELETE FROM delivery_attempts")
        jdbc.update("DELETE FROM confirmation_outbox")
        jdbc.update("DELETE FROM booking_requests")
        jdbc.update("DELETE FROM bookings")
        jdbc.update("DELETE FROM demo_activity")
        jdbc.update(
            "UPDATE demo_settings SET value = 'false' WHERE key = 'simulate_notification_failure'",
        )

        // Re-seed bookings exactly as V2__seed.sql
        jdbc.update(
            """
            INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
            SELECT g, 1, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
            FROM generate_series(1, 22) AS g
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
            SELECT 100 + g, 2, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
            FROM generate_series(1, 19) AS g
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO bookings (id, session_id, attendee_id, status, created_at)
            SELECT 200 + g, 3, 99 + g, 'ACTIVE', TIMESTAMPTZ '2026-01-01 10:00:00+00'
            FROM generate_series(1, 40) AS g
            """.trimIndent(),
        )
        jdbc.queryForObject(
            "SELECT setval('bookings_id_seq', (SELECT MAX(id) FROM bookings))",
            Long::class.java,
        )

        demoActivity.record("SEED_RESET", "Seed data restored; Alice and Bob have no bookings")
    }

    fun counts(): Map<String, Any> {
        val pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM confirmation_outbox WHERE status = 'PENDING'",
            Int::class.java,
        )!!
        val accepted = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_messages", Int::class.java)!!
        val acks = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_acknowledgements", Int::class.java)!!
        return mapOf(
            "pendingConfirmations" to pending,
            "acceptedDeliveries" to accepted,
            "acknowledgements" to acks,
        )
    }
}
