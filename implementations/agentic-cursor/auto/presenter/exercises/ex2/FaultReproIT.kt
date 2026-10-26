package com.conference.planner

import com.conference.planner.booking.BookingService
import com.conference.planner.demo.SeedResetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic overbooking repro for the faulty snapshot.
 * Uses a file gate (conference.fault.probe) around the unlocked read/insert gap.
 */
class FaultReproIT : PostgresIntegrationTest() {
    @Autowired lateinit var bookingService: BookingService
    @Autowired lateinit var seedReset: SeedResetService
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    @Timeout(30)
    fun `faulty snapshot can overbook last place`() {
        seedReset.reset()
        val gate = Files.createTempDirectory("cp-fault-")
        System.setProperty("conference.fault.probe", gate.toString())
        try {
            val ok = AtomicInteger()
            val errors = mutableListOf<Throwable>()
            val pool = Executors.newFixedThreadPool(2)
            val f1 = pool.submit {
                try {
                    bookingService.createBooking(1, 2, "fault-a")
                    ok.incrementAndGet()
                } catch (ex: Throwable) {
                    synchronized(errors) { errors.add(ex) }
                }
            }
            val f2 = pool.submit {
                try {
                    bookingService.createBooking(2, 2, "fault-b")
                    ok.incrementAndGet()
                } catch (ex: Throwable) {
                    synchronized(errors) { errors.add(ex) }
                }
            }

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val ready = Files.list(gate).use { stream ->
                    stream.filter { it.fileName.toString().startsWith("ready-") }.count()
                }
                if (ready >= 2L) break
                Thread.sleep(5)
            }
            Files.writeString(gate.resolve("go"), "go")
            f1.get(10, TimeUnit.SECONDS)
            f2.get(10, TimeUnit.SECONDS)
            pool.shutdownNow()

            val active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE session_id = 2 AND status = 'ACTIVE'",
                Int::class.java,
            )
            assertThat(errors).isEmpty()
            assertThat(ok.get()).isEqualTo(2)
            assertThat(active).isEqualTo(21)
        } finally {
            System.clearProperty("conference.fault.probe")
        }
    }
}
