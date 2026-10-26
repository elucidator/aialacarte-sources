package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

/**
 * Presenter reproducer for exercise 4. It books one place, arms the one-shot gateway
 * timeout (message recorded, acknowledgement lost), then retries.
 *
 * Expected: FAILS on the candidate snapshot (two accepted messages for one booking) and
 * PASSES after presenter/reference-fixes/e4.patch (one accepted message).
 */
class DeliveryKeyReproducerIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    private fun armFailure() {
        val response = exchange(
            HttpMethod.POST,
            "/api/demo/simulate-notification-failure",
            body = """{"enabled":true}""",
        )
        assertThat(response.statusCode.value()).isEqualTo(200)
    }

    private fun dispatch() {
        val response = exchange(HttpMethod.POST, "/api/demo/dispatch")
        assertThat(response.statusCode.value()).isEqualTo(200)
    }

    private fun notificationFor(bookingId: Long) =
        json(getResponse("/api/demo/notifications"))
            .single { it.path("bookingId").asLong() == bookingId }

    @Test
    fun `retry after a lost acknowledgement leaves one accepted message per booking`() {
        val bookingId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()

        armFailure()
        dispatch()
        assertThat(notificationFor(bookingId).path("acceptedDeliveries").asInt())
            .describedAs("the gateway durably recorded the first attempt before the timeout")
            .isEqualTo(1)

        dispatch()
        val afterRetry = notificationFor(bookingId)
        assertThat(afterRetry.path("status").asText()).isEqualTo("ACKNOWLEDGED")
        assertThat(afterRetry.path("acceptedDeliveries").asInt())
            .describedAs("accepted gateway messages for one booking after a lost acknowledgement and retry")
            .isEqualTo(1)

        assertThat(json(getResponse("/api/bookings", attendee = "Alice")))
            .describedAs("the booking itself stays unique")
            .hasSize(1)
    }
}
