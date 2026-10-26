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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Presenter-only: after a lost acknowledgement, a fresh delivery key per attempt
 * yields two accepted gateway messages for one booking.
 */
class FreshKeyDefectIT : PostgresIntegrationTest() {
    @LocalServerPort private var port: Int = 0
    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var seedReset: SeedResetService
    @Autowired lateinit var dispatcher: ConfirmationDispatcher
    @Autowired lateinit var demoSettings: DemoSettingsRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun reset() {
        seedReset.reset()
        demoSettings.setSimulateNotificationFailure(false)
    }

    @Test
    fun `fresh keys produce two accepts after timeout`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Demo-Attendee"] = "1"
        headers["Idempotency-Key"] = "ex4-key"
        val book = rest.exchange(
            "http://localhost:$port/api/bookings",
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        val bookingId = (book.body!!["id"] as Number).toLong()

        demoSettings.setSimulateNotificationFailure(true)
        dispatcher.dispatchPending()
        demoSettings.setSimulateNotificationFailure(false)
        dispatcher.dispatchPending()

        val accepts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_messages WHERE booking_id = ?",
            Int::class.java,
            bookingId,
        )
        assertThat(accepts).isEqualTo(2)

        val bookings = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE id = ?",
            Int::class.java,
            bookingId,
        )
        assertThat(bookings).isEqualTo(1)
    }
}
