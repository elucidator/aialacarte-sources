package dev.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BookingApiTest : IntegrationTestBase() {

    private fun key() = UUID.randomUUID().toString()

    @Test
    fun `books a place and returns 201 with the booking`() {
        val response = http.book("alice", 1, key())
        assertThat(response.statusCode.value()).isEqualTo(201)
        val booking = http.tree(response)
        assertThat(booking["id"].asLong()).isEqualTo(1000L)
        assertThat(booking["sessionId"].asLong()).isEqualTo(1L)
        assertThat(booking["sessionTitle"].asText()).isEqualTo("Debugging Distributed Systems")
        assertThat(booking["attendeeId"].asText()).isEqualTo("alice")
        assertThat(booking["status"].asText()).isEqualTo("ACTIVE")
        assertThat(booking["createdAt"].asText()).isNotBlank()
        assertThat(http.session(1)["activeBookings"].asInt()).isEqualTo(23)
        assertThat(http.session(1)["availablePlaces"].asInt()).isEqualTo(7)
    }

    @Test
    fun `replaying a completed request returns the original booking with 200`() {
        val requestKey = key()
        val first = http.tree(http.book("alice", 1, requestKey))
        val replay = http.book("alice", 1, requestKey)
        assertThat(replay.statusCode.value()).isEqualTo(200)
        assertThat(http.tree(replay)["id"].asLong()).isEqualTo(first["id"].asLong())
        assertThat(http.session(1)["activeBookings"].asInt()).isEqualTo(23)
    }

    @Test
    fun `request keys are scoped per attendee`() {
        val shared = key()
        assertThat(http.book("alice", 1, shared).statusCode.value()).isEqualTo(201)
        assertThat(http.book("bob", 1, shared).statusCode.value()).isEqualTo(201)
    }

    @Test
    fun `reusing a request key with a different payload returns 409 REQUEST_KEY_REUSED`() {
        val requestKey = key()
        http.book("alice", 1, requestKey)
        val response = http.book("alice", 2, requestKey)
        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(http.tree(response)["code"].asText()).isEqualTo("REQUEST_KEY_REUSED")
    }

    @Test
    fun `a second active booking for the same session returns 409 ALREADY_BOOKED`() {
        http.book("alice", 1, key())
        val response = http.book("alice", 1, key())
        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(http.tree(response)["code"].asText()).isEqualTo("ALREADY_BOOKED")
        assertThat(http.session(1)["activeBookings"].asInt()).isEqualTo(23)
    }

    @Test
    fun `a full session returns 409 SESSION_FULL and leaves no trace`() {
        val response = http.book("alice", 3, key())
        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(http.tree(response)["code"].asText()).isEqualTo("SESSION_FULL")
        assertThat(http.session(3)["activeBookings"].asInt()).isEqualTo(40)
        assertThat(http.tree(http.get("/api/bookings", "alice")).size()).isZero()
        assertThat(http.activity()["confirmations"].size()).isZero()
    }

    @Test
    fun `unknown session returns 404 SESSION_NOT_FOUND`() {
        val response = http.book("alice", 999, key())
        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(http.tree(response)["code"].asText()).isEqualTo("SESSION_NOT_FOUND")
    }

    @Test
    fun `missing identity, missing key, unknown attendee and malformed input return 400`() {
        fun codeOf(response: org.springframework.http.ResponseEntity<String>): Pair<Int, String> =
            response.statusCode.value() to http.tree(response)["code"].asText()

        assertThat(codeOf(http.post("/api/bookings", """{"sessionId": 1}""", attendee = null, requestKey = key()))).isEqualTo(400 to "MISSING_ATTENDEE")
        assertThat(codeOf(http.post("/api/bookings", """{"sessionId": 1}""", attendee = "alice", requestKey = null))).isEqualTo(400 to "MISSING_REQUEST_KEY")
        assertThat(codeOf(http.post("/api/bookings", """{"sessionId": 1}""", attendee = "mallory", requestKey = key()))).isEqualTo(400 to "UNKNOWN_ATTENDEE")
        assertThat(codeOf(http.post("/api/bookings", """{"sessionId": "one"}""", attendee = "alice", requestKey = key()))).isEqualTo(400 to "VALIDATION_ERROR")
        assertThat(codeOf(http.post("/api/bookings", """{}""", attendee = "alice", requestKey = key()))).isEqualTo(400 to "VALIDATION_ERROR")
        assertThat(codeOf(http.post("/api/bookings", """not json""", attendee = "alice", requestKey = key()))).isEqualTo(400 to "VALIDATION_ERROR")
        assertThat(codeOf(http.post("/api/bookings", null, attendee = "alice", requestKey = key()))).isEqualTo(400 to "VALIDATION_ERROR")
        assertThat(codeOf(http.get("/api/bookings"))).isEqualTo(400 to "MISSING_ATTENDEE")
        assertThat(codeOf(http.get("/api/bookings", "mallory"))).isEqualTo(400 to "UNKNOWN_ATTENDEE")
        assertThat(codeOf(http.delete("/api/bookings/1000"))).isEqualTo(400 to "MISSING_ATTENDEE")
        assertThat(codeOf(http.delete("/api/bookings/abc", "alice"))).isEqualTo(400 to "VALIDATION_ERROR")
    }

    @Test
    fun `lists only the attendee's active bookings`() {
        val mine = http.tree(http.book("alice", 1, key()))["id"].asLong()
        http.book("bob", 1, key())
        val list = http.tree(http.get("/api/bookings", "alice")).values().toList()
        assertThat(list.map { it["id"].asLong() }).containsExactly(mine)
        assertThat(list[0]["sessionTitle"].asText()).isEqualTo("Debugging Distributed Systems")
    }

    @Test
    fun `cancellation releases the place exactly once`() {
        val id = http.tree(http.book("alice", 2, key()))["id"].asLong()
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(0)

        assertThat(http.delete("/api/bookings/$id", "alice").statusCode.value()).isEqualTo(204)
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(1)
        assertThat(http.tree(http.get("/api/bookings", "alice")).size()).isZero()

        // Repeating the cancellation is a no-op, still 204, and does not release another place.
        assertThat(http.delete("/api/bookings/$id", "alice").statusCode.value()).isEqualTo(204)
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(1)
        assertThat(http.session(2)["activeBookings"].asInt()).isEqualTo(19)
    }

    @Test
    fun `cancelling a missing or foreign booking returns 404 BOOKING_NOT_FOUND`() {
        val id = http.tree(http.book("alice", 1, key()))["id"].asLong()
        val foreign = http.delete("/api/bookings/$id", "bob")
        assertThat(foreign.statusCode.value()).isEqualTo(404)
        assertThat(http.tree(foreign)["code"].asText()).isEqualTo("BOOKING_NOT_FOUND")
        val missing = http.delete("/api/bookings/424242", "alice")
        assertThat(missing.statusCode.value()).isEqualTo(404)
        assertThat(http.tree(missing)["code"].asText()).isEqualTo("BOOKING_NOT_FOUND")
        assertThat(http.session(1)["activeBookings"].asInt()).isEqualTo(23)
    }

    @Test
    fun `replay after cancellation returns the cancelled booking and does not rebook`() {
        val requestKey = key()
        val id = http.tree(http.book("alice", 2, requestKey))["id"].asLong()
        http.delete("/api/bookings/$id", "alice")

        val replay = http.book("alice", 2, requestKey)
        assertThat(replay.statusCode.value()).isEqualTo(200)
        assertThat(http.tree(replay)["id"].asLong()).isEqualTo(id)
        assertThat(http.tree(replay)["status"].asText()).isEqualTo("CANCELLED")
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(1)

        // A fresh key books again.
        val again = http.book("alice", 2, key())
        assertThat(again.statusCode.value()).isEqualTo(201)
        assertThat(http.tree(again)["id"].asLong()).isNotEqualTo(id)
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(0)
    }
}
