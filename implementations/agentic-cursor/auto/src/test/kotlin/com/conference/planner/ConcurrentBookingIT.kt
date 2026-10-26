package com.conference.planner

import com.conference.planner.demo.SeedResetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies last-place capacity with concurrent requests.
 * [TwoProcessCapacityIT] additionally checks two JVM processes.
 */
class ConcurrentBookingIT : PostgresIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var seedReset: SeedResetService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var rest: TestRestTemplate

    @BeforeEach
    fun reset() {
        seedReset.reset()
    }

    @Test
    @Timeout(30)
    fun `last place cannot be overbooked by concurrent attendees`() {
        // Session 2 has capacity 20 and 19 active => 1 place
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val pool = Executors.newFixedThreadPool(2)
        val created = AtomicInteger()
        val conflicts = AtomicInteger()

        val tasks = listOf(
            Callable { book(client, attendeeId = 1, key = "alice-last") },
            Callable { book(client, attendeeId = 2, key = "bob-last") },
        )
        val results = pool.invokeAll(tasks, 20, TimeUnit.SECONDS)
        pool.shutdownNow()

        for (f in results) {
            val code = f.get()
            when (code) {
                201 -> created.incrementAndGet()
                409 -> conflicts.incrementAndGet()
                else -> error("Unexpected status $code")
            }
        }

        assertThat(created.get()).isEqualTo(1)
        assertThat(conflicts.get()).isEqualTo(1)

        val active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE session_id = 2 AND status = 'ACTIVE'",
            Int::class.java,
        )
        assertThat(active).isEqualTo(20)
    }

    private fun book(client: HttpClient, attendeeId: Long, key: String): Int {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/bookings"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-Demo-Attendee", attendeeId.toString())
            .header("Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString("""{"sessionId":2}"""))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    @Test
    fun `ui index is served`() {
        val res = rest.getForEntity("http://localhost:$port/", String::class.java)
        assertThat(res.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(res.body).contains("Conference Planner")
        assertThat(res.body).contains("Demo identity")
    }
}
