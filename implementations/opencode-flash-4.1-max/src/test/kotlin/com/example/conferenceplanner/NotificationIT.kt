package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class NotificationIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    private fun armFailure(enabled: Boolean) {
        val response = exchange(
            org.springframework.http.HttpMethod.POST,
            "/api/demo/simulate-notification-failure",
            body = """{"enabled":$enabled}""",
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    private fun dispatch(): com.fasterxml.jackson.databind.JsonNode {
        val response = exchange(org.springframework.http.HttpMethod.POST, "/api/demo/dispatch")
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        return json(response)
    }

    @Test
    fun `accepted booking persists with a pending confirmation and deduplicated retry`() {
        val bookingId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()

        var notifications = json(getResponse("/api/demo/notifications"))
        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].path("status").asText()).isEqualTo("PENDING")
        assertThat(notifications[0].path("deliveryKey").asText()).isEqualTo("booking:$bookingId")
        assertThat(notifications[0].path("acceptedDeliveries").asInt()).isEqualTo(0)

        // First dispatch: gateway commits the message, then the acknowledgement is lost.
        armFailure(true)
        val first = dispatch()
        assertThat(first.path("attempted").asInt()).isEqualTo(1)
        assertThat(first.path("timedOut").asInt()).isEqualTo(1)
        assertThat(first.path("acknowledged").asInt()).isEqualTo(0)
        assertThat(first.path("stillPending").asInt()).isEqualTo(1)

        notifications = json(getResponse("/api/demo/notifications"))
        assertThat(notifications[0].path("status").asText()).isEqualTo("PENDING")
        assertThat(notifications[0].path("attempts").asInt()).isEqualTo(1)
        assertThat(notifications[0].path("acceptedDeliveries").asInt())
            .describedAs("gateway recorded the message even though the sender saw a timeout")
            .isEqualTo(1)

        // Second dispatch: stable delivery key is deduplicated, then acknowledged.
        val second = dispatch()
        assertThat(second.path("duplicates").asInt()).isEqualTo(1)
        assertThat(second.path("accepted").asInt()).isEqualTo(0)
        assertThat(second.path("acknowledged").asInt()).isEqualTo(1)
        assertThat(second.path("stillPending").asInt()).isEqualTo(0)

        notifications = json(getResponse("/api/demo/notifications"))
        assertThat(notifications[0].path("status").asText()).isEqualTo("ACKNOWLEDGED")
        assertThat(notifications[0].path("attempts").asInt()).isEqualTo(2)
        assertThat(notifications[0].path("acceptedDeliveries").asInt())
            .describedAs("repeated attempts produced exactly one accepted message")
            .isEqualTo(1)

        // The accepted booking stays accepted.
        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(23)

        val kinds = json(getResponse("/api/demo/activity")).map { it.path("kind").asText() }
        assertThat(kinds).contains("BOOKING_CREATED", "DELIVERY_TIMEOUT", "DELIVERY_ACKNOWLEDGED")
    }

    @Test
    fun `each booking gets its own stable delivery key and one accepted message`() {
        val firstId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()
        val secondId = json(postBooking("Bob", newKey(), sessionId = 2)).path("id").asLong()

        val summary = dispatch()
        assertThat(summary.path("attempted").asInt()).isEqualTo(2)
        assertThat(summary.path("accepted").asInt()).isEqualTo(2)
        assertThat(summary.path("acknowledged").asInt()).isEqualTo(2)
        assertThat(summary.path("stillPending").asInt()).isEqualTo(0)

        val notifications = json(getResponse("/api/demo/notifications"))
        val keys = notifications.map { it.path("deliveryKey").asText() }
        assertThat(keys).containsExactlyInAnyOrder("booking:$firstId", "booking:$secondId")
        assertThat(notifications.all { it.path("acceptedDeliveries").asInt() == 1 }).isTrue()

        // A further dispatch has nothing to do and does not duplicate messages.
        val again = dispatch()
        assertThat(again.path("attempted").asInt()).isEqualTo(0)
        val after = json(getResponse("/api/demo/notifications"))
        assertThat(after.all { it.path("acceptedDeliveries").asInt() == 1 }).isTrue()
    }

    @Test
    fun `reset clears requests notifications attempts deliveries and activity`() {
        postBooking("Alice", newKey(), sessionId = 1)
        armFailure(true)
        dispatch()
        assertThat(json(getResponse("/api/demo/state")).path("totalBookings").asLong()).isEqualTo(82)

        resetDemo()

        val state = json(getResponse("/api/demo/state"))
        assertThat(state.path("totalBookings").asLong()).isEqualTo(81)
        assertThat(state.path("pendingNotifications").asLong()).isEqualTo(0)
        assertThat(state.path("acknowledgedNotifications").asLong()).isEqualTo(0)
        assertThat(state.path("totalActivity").asLong()).isEqualTo(0)
        assertThat(state.path("controls").path("simulate_notification_failure").asText()).isEqualTo("false")
        assertThat(json(getResponse("/api/demo/notifications"))).isEmpty()
        assertThat(json(getResponse("/api/demo/activity"))).isEmpty()

        // Seed bookings never create pending notifications, and ids are deterministic after reset.
        val rebooked = postBooking("Alice", "key-after-reset", sessionId = 1)
        assertThat(rebooked.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(json(rebooked).path("id").asLong()).isEqualTo(82)
    }
}
