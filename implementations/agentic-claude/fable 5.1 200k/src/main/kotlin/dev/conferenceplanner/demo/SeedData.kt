package dev.conferenceplanner.demo

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Deterministic seed: three sessions (ids 1..3), demo attendees alice and bob, forty fictional
 * seed attendees and 81 seed bookings (ids 1..81). New bookings start at id 1000.
 * Seed bookings have no confirmations, so they never produce notifications.
 */
@Component
class SeedData(private val jdbc: JdbcClient, transactionManager: PlatformTransactionManager) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager)

    override fun run(args: ApplicationArguments) {
        transaction.executeWithoutResult {
            jdbc.sql("SELECT pg_advisory_xact_lock($SEED_LOCK)").query().listOfRows()
            val sessions = jdbc.sql("SELECT count(*) FROM sessions").query(Int::class.javaObjectType).single()
            if (sessions == 0) {
                insertSeed()
                log.info("Seed data inserted")
            }
        }
    }

    /** Restores the exact seed state and clears everything the demo produced. */
    fun reset() {
        transaction.executeWithoutResult {
            jdbc.sql("SELECT pg_advisory_xact_lock($SEED_LOCK)").query().listOfRows()
            jdbc.sql(
                """
                TRUNCATE demo_activity, delivery_attempts, confirmations, gateway_messages, gateway_settings,
                         booking_requests, bookings, sessions, attendees RESTART IDENTITY
                """,
            ).update()
            insertSeed()
        }
    }

    private fun insertSeed() {
        jdbc.sql("INSERT INTO gateway_settings (id, lose_acknowledgements) VALUES (1, FALSE)").update()

        jdbc.sql("INSERT INTO attendees (id, display_name) VALUES ('alice', 'Alice (demo)'), ('bob', 'Bob (demo)')").update()
        for (n in 1..40) {
            jdbc.sql("INSERT INTO attendees (id, display_name) VALUES (:id, :name)")
                .param("id", seedAttendee(n)).param("name", "Seed Attendee %02d".format(n)).update()
        }

        jdbc.sql(
            """
            INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES
              (1, 'Debugging Distributed Systems', 'Room A', 30, 1),
              (2, 'Practical Kotlin',              'Room B', 20, 2),
              (3, 'Understanding Databases',       'Room C', 40, 3)
            """,
        ).update()

        var bookingId = 0L
        for ((sessionId, seeded) in listOf(1L to 22, 2L to 19, 3L to 40)) {
            for (n in 1..seeded) {
                bookingId++
                jdbc.sql("INSERT INTO bookings (id, session_id, attendee_id, status) VALUES (:id, :session, :attendee, 'ACTIVE')")
                    .param("id", bookingId).param("session", sessionId).param("attendee", seedAttendee(n)).update()
            }
        }
        jdbc.sql("SELECT setval(pg_get_serial_sequence('bookings', 'id'), $FIRST_NEW_BOOKING_ID, false)").query().listOfRows()
    }

    private fun seedAttendee(n: Int) = "seed-%02d".format(n)

    private companion object {
        const val SEED_LOCK = 42_001L
        const val FIRST_NEW_BOOKING_ID = 1000L
    }
}
