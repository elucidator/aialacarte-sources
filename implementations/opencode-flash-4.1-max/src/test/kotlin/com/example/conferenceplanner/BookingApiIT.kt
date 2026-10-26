package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class BookingApiIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    @Test
    fun `creates a booking and reflects it in listings and availability`() {
        val response = postBooking("Alice", "key-alice-1", sessionId = 1)
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)

        val booking = json(response)
        assertThat(booking.path("attendee").asText()).isEqualTo("Alice")
        assertThat(booking.path("sessionId").asLong()).isEqualTo(1)
        assertThat(booking.path("sessionTitle").asText()).isEqualTo("Debugging Distributed Systems")
        assertThat(booking.path("status").asText()).isEqualTo("ACTIVE")
        assertThat(booking.path("id").asLong()).isGreaterThan(81)

        val mine = json(getResponse("/api/bookings", attendee = "Alice"))
        assertThat(mine).hasSize(1)
        assertThat(mine[0].path("id").asLong()).isEqualTo(booking.path("id").asLong())

        val session = json(getResponse("/api/sessions"))["items"][0]
        assertThat(session.path("activeBookings").asInt()).isEqualTo(23)
        assertThat(session.path("availablePlaces").asInt()).isEqualTo(7)
    }

    @Test
    fun `replaying a completed request returns the original booking with 200`() {
        val first = postBooking("Alice", "replay-key", sessionId = 1)
        assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)

        val second = postBooking("Alice", "replay-key", sessionId = 1)
        assertThat(second.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(json(second).path("id").asLong()).isEqualTo(json(first).path("id").asLong())

        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(23)
    }

    @Test
    fun `reusing a request key with a different payload returns 409`() {
        assertThat(postBooking("Alice", "cross-key", sessionId = 1).statusCode).isEqualTo(HttpStatus.CREATED)

        val conflict = postBooking("Alice", "cross-key", sessionId = 2)
        assertThat(conflict.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED")
    }

    @Test
    fun `a second active booking for the same attendee and session returns 409`() {
        assertThat(postBooking("Alice", newKey(), sessionId = 1).statusCode).isEqualTo(HttpStatus.CREATED)

        val duplicate = postBooking("Alice", newKey(), sessionId = 1)
        assertThat(duplicate.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode(duplicate)).isEqualTo("DUPLICATE_BOOKING")
    }

    @Test
    fun `booking a full session returns 409`() {
        val full = postBooking("Bob", newKey(), sessionId = 3)
        assertThat(full.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode(full)).isEqualTo("SESSION_FULL")
    }

    @Test
    fun `unknown session returns 404`() {
        val response = postBooking("Alice", newKey(), sessionId = 999)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(errorCode(response)).isEqualTo("SESSION_NOT_FOUND")
    }

    @Test
    fun `missing identity key or malformed body returns 400`() {
        val noAttendee = postBooking(null, newKey(), sessionId = 1)
        assertThat(noAttendee.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(noAttendee)).isEqualTo("MISSING_ATTENDEE")

        val blankAttendee = postBooking("   ", newKey(), sessionId = 1)
        assertThat(blankAttendee.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(blankAttendee)).isEqualTo("MISSING_ATTENDEE")

        val noKey = postBooking("Alice", null, sessionId = 1)
        assertThat(noKey.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(noKey)).isEqualTo("MISSING_IDEMPOTENCY_KEY")

        val noBody = postBooking("Alice", newKey(), sessionId = 1, body = "{}")
        assertThat(noBody.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(noBody)).isEqualTo("INVALID_REQUEST")

        val broken = postBooking("Alice", newKey(), sessionId = 1, body = "not json")
        assertThat(broken.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(broken)).isEqualTo("INVALID_REQUEST")

        val zero = postBooking("Alice", newKey(), sessionId = 0)
        assertThat(zero.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(zero)).isEqualTo("INVALID_SESSION_ID")
    }

    @Test
    fun `cancellation frees a place once and is idempotent`() {
        val bookingId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()

        val cancel = cancelBooking("Alice", bookingId)
        assertThat(cancel.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).isEmpty()
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(22)

        val cancelAgain = cancelBooking("Alice", bookingId)
        assertThat(cancelAgain.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(22)
    }

    @Test
    fun `cancelling a missing or foreign booking returns 404`() {
        val missing = cancelBooking("Alice", 999_999)
        assertThat(missing.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(errorCode(missing)).isEqualTo("BOOKING_NOT_FOUND")

        val bookingId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()
        val foreign = cancelBooking("Bob", bookingId)
        assertThat(foreign.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(errorCode(foreign)).isEqualTo("BOOKING_NOT_FOUND")

        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
    }

    @Test
    fun `replay after cancellation returns the cancelled booking and does not rebook`() {
        val first = json(postBooking("Alice", "cancel-replay", sessionId = 1))
        val bookingId = first.path("id").asLong()
        assertThat(cancelBooking("Alice", bookingId).statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        val replay = postBooking("Alice", "cancel-replay", sessionId = 1)
        assertThat(replay.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(json(replay).path("id").asLong()).isEqualTo(bookingId)
        assertThat(json(replay).path("status").asText()).isEqualTo("CANCELLED")

        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).isEmpty()
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(22)
    }

    @Test
    fun `a fresh key after cancellation creates a new booking`() {
        val bookingId = json(postBooking("Alice", newKey(), sessionId = 1)).path("id").asLong()
        cancelBooking("Alice", bookingId)

        val rebooked = postBooking("Alice", newKey(), sessionId = 1)
        assertThat(rebooked.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(json(rebooked).path("id").asLong()).isNotEqualTo(bookingId)
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(23)
    }

    @Test
    fun `bookings are scoped to the demo attendee`() {
        assertThat(postBooking("Alice", newKey(), sessionId = 1).statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(postBooking("Bob", newKey(), sessionId = 1).statusCode).isEqualTo(HttpStatus.CREATED)

        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
        assertThat(json(getResponse("/api/bookings", attendee = "Bob"))).hasSize(1)
        assertThat(json(getResponse("/api/bookings", attendee = "Unknown"))).isEmpty()
    }
}
