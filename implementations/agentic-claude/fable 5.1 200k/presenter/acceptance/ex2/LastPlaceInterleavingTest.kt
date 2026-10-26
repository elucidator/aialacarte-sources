package dev.conferenceplanner.acceptance

import dev.conferenceplanner.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Deterministic reproducer for exercise 2. Two attendees book the last place of a session at
 * the same time. The test forces the critical interleaving instead of hoping for it:
 *
 *  1. A helper connection takes `LOCK TABLE bookings IN SHARE MODE`. That blocks INSERTs into
 *     bookings, but not SELECTs, not the `FOR UPDATE` lock on the sessions row and not the
 *     ROW SHARE lock that foreign-key checks take on bookings (booking_requests references it).
 *  2. Both bookings are fired. With a correct implementation the first booker holds the
 *     session row lock and waits at its INSERT; the second waits for the row lock. With the
 *     faulty implementation both count 39 < 40 and both wait at their INSERT.
 *  3. The test waits (bounded, 10 s) until two backends are waiting on a lock, then commits the
 *     helper transaction. Everything that follows is decided by the application's own
 *     coordination, not by timing.
 *
 * Expected with correct coordination: one 201 and one 409 SESSION_FULL, 40 active bookings.
 * With EVIDENCE_DIR set, the observed responses and rows are written there before asserting.
 */
class LastPlaceInterleavingTest : IntegrationTestBase() {

    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var jdbc: JdbcClient

    private val sessionId = 3L

    @Test
    fun `two bookers racing for the last place get one 201 and one 409`() {
        // Exactly one free place: 39 of 40 booked (one seed booking removed from the picture).
        jdbc.sql("DELETE FROM bookings WHERE id = 81").update()
        assertThat(http.session(sessionId)["availablePlaces"].asInt()).isEqualTo(1)

        val pool = Executors.newFixedThreadPool(2)
        val results: List<ResponseEntity<String>>
        val gate = dataSource.connection
        try {
            gate.autoCommit = false
            gate.createStatement().execute("LOCK TABLE bookings IN SHARE MODE")

            val futures = listOf("alice", "bob").map { attendee ->
                pool.submit<ResponseEntity<String>> { http.book(attendee, sessionId, UUID.randomUUID().toString()) }
            }
            waitUntilBothBlocked(gate)
            gate.commit()
            results = futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            runCatching { gate.rollback() }
            gate.close()
            pool.shutdownNow()
        }

        val statuses = results.map { it.statusCode.value() }.sorted()
        val session = http.session(sessionId)
        val rows = jdbc.sql("SELECT id, attendee_id, status, created_at FROM bookings WHERE session_id = :s AND status = 'ACTIVE' ORDER BY id")
            .param("s", sessionId).query().listOfRows()
        writeEvidence(results, session.toString(), rows.joinToString("\n"))

        assertThat(statuses).describedAs("HTTP statuses of the two concurrent bookings").containsExactly(201, 409)
        assertThat(http.tree(results.first { it.statusCode.value() == 409 })["code"].asText()).isEqualTo("SESSION_FULL")
        assertThat(session["activeBookings"].asInt()).describedAs("active bookings after the race").isEqualTo(40)
        assertThat(rows).hasSize(40)
    }

    /** Polls pg_stat_activity until two backends of this database wait on a lock; bounded by 10 s. */
    private fun waitUntilBothBlocked(gate: java.sql.Connection) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            gate.createStatement().executeQuery(
                "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
            ).use { rs -> rs.next(); if (rs.getInt(1) >= 2) return }
            Thread.sleep(50)
        }
        val waiting = StringBuilder()
        gate.createStatement().executeQuery("SELECT pid, locktype, coalesce(relation::regclass::text, ''), mode FROM pg_locks WHERE NOT granted").use { rs ->
            while (rs.next()) waiting.append("\n  pid ").append(rs.getInt(1)).append(' ').append(rs.getString(2)).append(' ').append(rs.getString(3)).append(' ').append(rs.getString(4))
        }
        error("timed out after 10 s waiting for both bookers to block on a lock; ungranted locks:$waiting")
    }

    private fun writeEvidence(results: List<ResponseEntity<String>>, sessionJson: String, rows: String) {
        val dir = System.getenv("EVIDENCE_DIR")?.takeIf { it.isNotBlank() } ?: return
        val out = Paths.get(dir)
        Files.createDirectories(out)
        val responses = results.mapIndexed { i, r ->
            "POST /api/bookings  X-Demo-Attendee: ${listOf("alice", "bob")[i]}  body {\"sessionId\": $sessionId}\nHTTP ${r.statusCode.value()}\n${r.body}\n"
        }.joinToString("\n")
        Files.writeString(out.resolve("http-responses.txt"), responses)
        Files.writeString(out.resolve("session-3-after.json"), sessionJson + "\n")
        Files.writeString(out.resolve("bookings-session-3-active.txt"), "id | attendee_id | status | created_at\n$rows\n")
        Files.writeString(out.resolve("activity.json"), http.activity()["activity"].toPrettyString() + "\n")
    }
}
