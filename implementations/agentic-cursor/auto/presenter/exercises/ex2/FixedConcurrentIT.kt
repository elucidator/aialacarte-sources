package com.conference.planner

import com.conference.planner.booking.BookingService
import com.conference.planner.demo.SeedResetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Independent correctness check for the repaired booking path.
 * No file barrier — a correct serialised implementation must not deadlock.
 */
class FixedConcurrentIT : PostgresIntegrationTest() {
    @Autowired lateinit var bookingService: BookingService
    @Autowired lateinit var seedReset: SeedResetService
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    @Timeout(30)
    fun `repaired code cannot overbook last place`() {
        seedReset.reset()
        val ok = AtomicInteger()
        val conflict = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        val futures = pool.invokeAll(
            listOf(
                Callable {
                    try {
                        bookingService.createBooking(1, 2, "fix-a")
                        ok.incrementAndGet()
                    } catch (_: Exception) {
                        conflict.incrementAndGet()
                    }
                },
                Callable {
                    try {
                        bookingService.createBooking(2, 2, "fix-b")
                        ok.incrementAndGet()
                    } catch (_: Exception) {
                        conflict.incrementAndGet()
                    }
                },
            ),
            20,
            TimeUnit.SECONDS,
        )
        futures.forEach { it.get() }
        pool.shutdownNow()
        val active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE session_id = 2 AND status = 'ACTIVE'",
            Int::class.java,
        )
        assertThat(ok.get()).isEqualTo(1)
        assertThat(conflict.get()).isEqualTo(1)
        assertThat(active).isEqualTo(20)
    }
}
