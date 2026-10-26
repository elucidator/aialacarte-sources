package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Presenter acceptance check for exercise 1. Copied into the exported workspace by
 * presenter/acceptance/e1.sh. It must fail before the reference patch and pass after it.
 */
class AvailableOnlyIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    private fun sessionIds(path: String): List<Long> =
        json(getResponse(path)).path("items").map { it.path("id").asLong() }

    @Test
    fun `default listing keeps all sessions`() {
        assertThat(sessionIds("/api/sessions")).containsExactly(1L, 2L, 3L)
        assertThat(json(getResponse("/api/sessions?availableOnly=false")).path("totalElements").asLong())
            .isEqualTo(3L)
    }

    @Test
    fun `availableOnly true returns only sessions with places`() {
        val body = json(getResponse("/api/sessions?availableOnly=true"))
        assertThat(body.path("totalElements").asLong()).isEqualTo(2L)
        assertThat(body.path("items").map { it.path("id").asLong() }).containsExactly(1L, 2L)
    }

    @Test
    fun `filter applies before pagination and totalElements covers the filtered set`() {
        val body = json(getResponse("/api/sessions?availableOnly=true&page=1&size=1"))
        assertThat(body.path("page").asInt()).isEqualTo(1)
        assertThat(body.path("totalElements").asLong()).isEqualTo(2L)
        assertThat(body.path("items").map { it.path("id").asLong() }).containsExactly(2L)
    }

    @Test
    fun `booking and cancelling move a session in and out of the filtered result`() {
        assertThat(sessionIds("/api/sessions?availableOnly=true")).contains(2L)

        val booking = json(postBooking("Alice", newKey(), sessionId = 2))
        assertThat(sessionIds("/api/sessions?availableOnly=true")).doesNotContain(2L)

        val cancelled = cancelBooking("Alice", booking.path("id").asLong())
        assertThat(cancelled.statusCode.value()).isEqualTo(204)
        assertThat(sessionIds("/api/sessions?availableOnly=true")).contains(2L)
    }

    @Test
    fun `invalid boolean returns 400`() {
        val response = getResponse("/api/sessions?availableOnly=banana")
        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(errorCode(response)).isEqualTo("INVALID_PARAMETER")
    }

    @Test
    fun `UI checkbox is present and wired to the API parameter`() {
        val index = rest.getForEntity("/", String::class.java).body!!
        assertThat(index).contains("available-only")
        val script = rest.getForEntity("/app.js", String::class.java).body!!
        assertThat(script).contains("availableOnly")
    }
}
