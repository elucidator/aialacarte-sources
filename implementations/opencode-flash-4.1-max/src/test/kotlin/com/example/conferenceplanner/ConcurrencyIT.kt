package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrencyIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    private fun runConcurrently(tasks: List<() -> ResponseEntity<String>>): List<ResponseEntity<String>> {
        val pool = Executors.newFixedThreadPool(tasks.size)
        try {
            val startGate = CountDownLatch(1)
            val futures = tasks.map { task ->
                pool.submit<ResponseEntity<String>> {
                    startGate.await(5, TimeUnit.SECONDS)
                    task()
                }
            }
            startGate.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `different attendees racing for the last place produce exactly one booking`() {
        val responses = runConcurrently(
            (0 until 8).map { index ->
                { postBooking("Racer $index", newKey(), sessionId = 2) }
            },
        )

        val created = responses.count { it.statusCode.value() == 201 }
        val full = responses.count {
            it.statusCode.value() == 409 && errorCode(it) == "SESSION_FULL"
        }
        assertThat(created).describedAs("exactly one attendee wins the last place").isEqualTo(1)
        assertThat(full).isEqualTo(7)

        val session = json(getResponse("/api/sessions"))["items"][1]
        assertThat(session.path("activeBookings").asInt()).isEqualTo(20)
        assertThat(session.path("availablePlaces").asInt()).isEqualTo(0)
    }

    @Test
    fun `concurrent requests with different keys cannot create two active bookings for one attendee`() {
        val responses = runConcurrently(
            (0 until 5).map {
                { postBooking("Alice", newKey(), sessionId = 1) }
            },
        )

        assertThat(responses.count { it.statusCode.value() == 201 }).isEqualTo(1)
        assertThat(responses.count {
            it.statusCode.value() == 409 && errorCode(it) == "DUPLICATE_BOOKING"
        }).isEqualTo(4)
        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
    }

    @Test
    fun `concurrent replays of one request key return the same booking`() {
        val key = newKey()
        val responses = runConcurrently(
            (0 until 6).map {
                { postBooking("Alice", key, sessionId = 1) }
            },
        )

        val ids = responses.map { json(it).path("id").asLong() }.distinct()
        assertThat(ids).hasSize(1)
        assertThat(responses.count { it.statusCode.value() == 201 }).isEqualTo(1)
        assertThat(responses.count { it.statusCode.value() == 200 }).isEqualTo(5)
        assertThat(json(getResponse("/api/bookings", attendee = "Alice"))).hasSize(1)
        assertThat(json(getResponse("/api/sessions"))["items"][0].path("activeBookings").asInt()).isEqualTo(23)
    }
}
