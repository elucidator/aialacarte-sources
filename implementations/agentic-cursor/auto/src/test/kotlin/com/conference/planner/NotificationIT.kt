package com.conference.planner

import com.conference.planner.demo.DemoSettingsRepository
import com.conference.planner.demo.SeedResetService
import com.conference.planner.notify.ConfirmationDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate

class NotificationIT : PostgresIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var seedReset: SeedResetService

    @Autowired
    lateinit var dispatcher: ConfirmationDispatcher

    @Autowired
    lateinit var demoSettings: DemoSettingsRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun reset() {
        seedReset.reset()
        demoSettings.setSimulateNotificationFailure(false)
    }

    @Test
    fun `confirmation is deduplicated across retries after timeout`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Demo-Attendee"] = "1"
        headers["Idempotency-Key"] = "notify-1"

        val book = rest.exchange(
            "http://localhost:$port/api/bookings",
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(book.statusCode).isEqualTo(HttpStatus.CREATED)
        val bookingId = (book.body!!["id"] as Number).toLong()

        demoSettings.setSimulateNotificationFailure(true)
        val first = dispatcher.dispatchPending()
        assertThat(first.failed).isGreaterThanOrEqualTo(1)

        val acceptedAfterFail = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_messages WHERE booking_id = ?",
            Int::class.java,
            bookingId,
        )
        assertThat(acceptedAfterFail).isEqualTo(1)

        val pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM confirmation_outbox WHERE booking_id = ? AND status = 'PENDING'",
            Int::class.java,
            bookingId,
        )
        assertThat(pending).isEqualTo(1)

        // Booking remains accepted
        val status = jdbc.queryForObject(
            "SELECT status FROM bookings WHERE id = ?",
            String::class.java,
            bookingId,
        )
        assertThat(status).isEqualTo("ACTIVE")

        demoSettings.setSimulateNotificationFailure(false)
        val second = dispatcher.dispatchPending()
        assertThat(second.acknowledged).isGreaterThanOrEqualTo(1)

        val acceptedFinal = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_messages WHERE booking_id = ?",
            Int::class.java,
            bookingId,
        )
        assertThat(acceptedFinal).isEqualTo(1)

        val acked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM confirmation_outbox WHERE booking_id = ? AND status = 'ACKNOWLEDGED'",
            Int::class.java,
            bookingId,
        )
        assertThat(acked).isEqualTo(1)
    }
}
