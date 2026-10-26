package dev.conferenceplanner.acceptance

import dev.conferenceplanner.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** Acceptance check for exercise 1: the availableOnly filter on the session list. */
class AvailableOnlyFilterTest : IntegrationTestBase() {

    private fun ids(query: String) = http.tree(http.get("/api/sessions?$query"))["items"].values().map { it["id"].asLong() }

    @Test
    fun `availableOnly=true hides full sessions and filters before pagination`() {
        val page = http.tree(http.get("/api/sessions?page=0&size=20&availableOnly=true"))
        assertThat(page["items"].values().map { it["id"].asLong() }).containsExactly(1L, 2L)
        assertThat(page["totalElements"].asLong()).isEqualTo(2)

        val second = http.tree(http.get("/api/sessions?page=1&size=1&availableOnly=true"))
        assertThat(second["items"].values().map { it["id"].asLong() }).containsExactly(2L)
        assertThat(second["totalElements"].asLong()).isEqualTo(2)
    }

    @Test
    fun `default and availableOnly=false keep the full list`() {
        assertThat(ids("page=0&size=20")).containsExactly(1L, 2L, 3L)
        assertThat(ids("page=0&size=20&availableOnly=false")).containsExactly(1L, 2L, 3L)
        assertThat(http.tree(http.get("/api/sessions?availableOnly=false"))["totalElements"].asLong()).isEqualTo(3)
    }

    @Test
    fun `filter follows bookings and cancellations`() {
        val booking = http.tree(http.book("alice", 2, UUID.randomUUID().toString()))
        assertThat(ids("availableOnly=true")).containsExactly(1L)
        http.delete("/api/bookings/${booking["id"].asLong()}", attendee = "alice")
        assertThat(ids("availableOnly=true")).containsExactly(1L, 2L)
    }

    @Test
    fun `anything but true or false is a 400 VALIDATION_ERROR`() {
        for (bad in listOf("yes", "1", "TRUE", "")) {
            val response = http.get("/api/sessions?availableOnly=$bad")
            assertThat(response.statusCode.value()).describedAs("availableOnly=$bad").isEqualTo(400)
            assertThat(http.tree(response)["code"].asText()).isEqualTo("VALIDATION_ERROR")
        }
    }
}
