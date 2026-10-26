package dev.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Independent correctness checks: many simultaneous requests inside one application process. */
class ConcurrencyTest : IntegrationTestBase() {

    private fun runConcurrently(count: Int, request: (Int) -> ResponseEntity<String>): List<ResponseEntity<String>> {
        val pool = Executors.newFixedThreadPool(count)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until count).map { i -> pool.submit<ResponseEntity<String>> { start.await(); request(i) } }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `only one of many simultaneous bookers gets the last place`() {
        val attendees = (20..31).map { "seed-%02d".format(it) }  // not yet booked in session 2
        val responses = runConcurrently(attendees.size) { i -> http.book(attendees[i], 2, UUID.randomUUID().toString()) }

        val statuses = responses.groupingBy { it.statusCode.value() }.eachCount()
        assertThat(statuses).isEqualTo(mapOf(201 to 1, 409 to attendees.size - 1))
        assertThat(responses.filter { it.statusCode.value() == 409 }.map { http.tree(it)["code"].asText() }).containsOnly("SESSION_FULL")
        assertThat(http.session(2)["activeBookings"].asInt()).isEqualTo(20)
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(0)
    }

    @Test
    fun `concurrent replays of one request key create one booking and all see it`() {
        val requestKey = UUID.randomUUID().toString()
        val responses = runConcurrently(8) { http.book("alice", 1, requestKey) }

        assertThat(responses.map { it.statusCode.value() }.sorted()).isEqualTo(listOf(200, 200, 200, 200, 200, 200, 200, 201))
        assertThat(responses.map { http.tree(it)["id"].asLong() }.toSet()).hasSize(1)
        assertThat(http.session(1)["activeBookings"].asInt()).isEqualTo(23)
        assertThat(http.tree(http.get("/api/bookings", "alice")).size()).isEqualTo(1)
    }

    @Test
    fun `concurrent different requests by one attendee for one session yield one booking`() {
        val responses = runConcurrently(8) { http.book("bob", 1, UUID.randomUUID().toString()) }
        assertThat(responses.count { it.statusCode.value() == 201 }).isEqualTo(1)
        assertThat(responses.filter { it.statusCode.value() != 201 }.map { http.tree(it)["code"].asText() }).containsOnly("ALREADY_BOOKED")
        assertThat(http.tree(http.get("/api/bookings", "bob")).size()).isEqualTo(1)
    }

    @Test
    fun `concurrent cancellations of one booking release a single place`() {
        val id = http.tree(http.book("alice", 2, UUID.randomUUID().toString()))["id"].asLong()
        val responses = runConcurrently(6) { http.delete("/api/bookings/$id", "alice") }
        assertThat(responses.map { it.statusCode.value() }).containsOnly(204)
        assertThat(http.session(2)["activeBookings"].asInt()).isEqualTo(19)
        assertThat(http.session(2)["availablePlaces"].asInt()).isEqualTo(1)
    }
}
