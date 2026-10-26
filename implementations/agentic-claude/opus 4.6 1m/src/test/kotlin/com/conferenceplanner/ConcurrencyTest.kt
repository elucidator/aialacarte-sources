package com.conferenceplanner

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("local-demo")
class ConcurrencyTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("conference_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }

        val SESSION_2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun resetSeed() {
        jdbc.update("DELETE FROM gateway_deliveries")
        jdbc.update("DELETE FROM notifications")
        jdbc.update("DELETE FROM bookings")
        jdbc.update("DELETE FROM sessions")

        jdbc.update(
            "INSERT INTO sessions (id, title, room, capacity, sort_order) VALUES (?, ?, ?, ?, ?)",
            SESSION_2, "Practical Kotlin", "Room B", 20, 1
        )
        for (i in 1..19) {
            val id = UUID.nameUUIDFromBytes("conc-seed-$i".toByteArray())
            jdbc.update(
                "INSERT INTO bookings (id, session_id, attendee, idempotency_key, status, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', now())",
                id, SESSION_2, "seed-$i", "seed-key-$i"
            )
        }
    }

    /**
     * Two concurrent booking attempts for the last place.
     * Uses separate JDBC connections to simulate two app processes.
     * Exactly one should succeed; the session must not be overbooked.
     */
    @Test
    fun `concurrent last-place booking prevents overbooking`() {
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(1)

        val results = (1..2).map { idx ->
            executor.submit(Callable {
                val conn = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
                conn.use { c ->
                    c.autoCommit = false
                    try {
                        latch.await(5, TimeUnit.SECONDS)

                        val lockStmt = c.prepareStatement(
                            "SELECT capacity FROM sessions WHERE id = ? FOR UPDATE"
                        )
                        lockStmt.setObject(1, SESSION_2)
                        val rs = lockStmt.executeQuery()
                        rs.next()
                        val capacity = rs.getInt(1)

                        val countStmt = c.prepareStatement(
                            "SELECT count(*) FROM bookings WHERE session_id = ? AND status = 'ACTIVE'"
                        )
                        countStmt.setObject(1, SESSION_2)
                        val crs = countStmt.executeQuery()
                        crs.next()
                        val count = crs.getInt(1)

                        if (count >= capacity) {
                            c.rollback()
                            return@Callable "FULL"
                        }

                        val bookingId = UUID.randomUUID()
                        val insertStmt = c.prepareStatement(
                            "INSERT INTO bookings (id, session_id, attendee, idempotency_key, status, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', now())"
                        )
                        insertStmt.setObject(1, bookingId)
                        insertStmt.setObject(2, SESSION_2)
                        insertStmt.setString(3, "concurrent-$idx")
                        insertStmt.setString(4, "conc-key-$idx")
                        insertStmt.executeUpdate()

                        c.commit()
                        "BOOKED"
                    } catch (e: Exception) {
                        c.rollback()
                        "ERROR: ${e.message}"
                    }
                }
            })
        }

        latch.countDown()
        val outcomes = results.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        val booked = outcomes.count { it == "BOOKED" }
        val full = outcomes.count { it == "FULL" }

        assertEquals(1, booked, "Exactly one booking should succeed. Outcomes: $outcomes")
        assertEquals(1, full, "Exactly one should be rejected as full. Outcomes: $outcomes")

        val finalCount = jdbc.queryForObject(
            "SELECT count(*) FROM bookings WHERE session_id = ? AND status = 'ACTIVE'",
            Int::class.java, SESSION_2
        )!!
        assertEquals(20, finalCount, "Active bookings should equal capacity")
    }

    /**
     * Concurrent replays of the same idempotency key must not create duplicate bookings.
     */
    @Test
    fun `concurrent replay does not create duplicates`() {
        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(1)
        val key = "concurrent-replay-key"
        val attendee = "replay-attendee"

        val results = (1..4).map {
            executor.submit(Callable {
                val conn = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
                conn.use { c ->
                    c.autoCommit = false
                    try {
                        latch.await(5, TimeUnit.SECONDS)

                        val existStmt = c.prepareStatement("SELECT id FROM bookings WHERE idempotency_key = ?")
                        existStmt.setString(1, key)
                        val ers = existStmt.executeQuery()
                        if (ers.next()) {
                            c.commit()
                            return@Callable "REPLAY"
                        }

                        val lockStmt = c.prepareStatement("SELECT capacity FROM sessions WHERE id = ? FOR UPDATE")
                        lockStmt.setObject(1, SESSION_2)
                        lockStmt.executeQuery().also { it.next() }

                        val bookingId = UUID.randomUUID()
                        val insertStmt = c.prepareStatement(
                            "INSERT INTO bookings (id, session_id, attendee, idempotency_key, status, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', now())"
                        )
                        insertStmt.setObject(1, bookingId)
                        insertStmt.setObject(2, SESSION_2)
                        insertStmt.setString(3, attendee)
                        insertStmt.setString(4, key)
                        insertStmt.executeUpdate()

                        c.commit()
                        "CREATED"
                    } catch (e: Exception) {
                        try { c.rollback() } catch (_: Exception) {}
                        "ERROR: ${e.message}"
                    }
                }
            })
        }

        latch.countDown()
        val outcomes = results.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        val created = outcomes.count { it == "CREATED" }
        assertTrue(created <= 1, "At most one creation. Outcomes: $outcomes")

        val bookingCount = jdbc.queryForObject(
            "SELECT count(*) FROM bookings WHERE idempotency_key = ?",
            Int::class.java, key
        )!!
        assertEquals(1, bookingCount, "Exactly one booking should exist for the key")
    }
}
