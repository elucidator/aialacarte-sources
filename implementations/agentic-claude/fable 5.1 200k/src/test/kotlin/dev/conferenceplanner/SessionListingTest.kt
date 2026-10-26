package dev.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SessionListingTest : IntegrationTestBase() {

    @Test
    fun `lists the seed sessions in stable order with derived availability`() {
        val response = http.get("/api/sessions?page=0&size=20")
        assertThat(response.statusCode.value()).isEqualTo(200)
        val page = http.tree(response)
        assertThat(page["page"].asInt()).isEqualTo(0)
        assertThat(page["size"].asInt()).isEqualTo(20)
        assertThat(page["totalElements"].asLong()).isEqualTo(3)

        val rows = page["items"].values().map { listOf(it["id"].asLong(), it["title"].asText(), it["room"].asText(), it["capacity"].asInt(), it["activeBookings"].asInt(), it["availablePlaces"].asInt()) }
        assertThat(rows).containsExactly(
            listOf(1L, "Debugging Distributed Systems", "Room A", 30, 22, 8),
            listOf(2L, "Practical Kotlin", "Room B", 20, 19, 1),
            listOf(3L, "Understanding Databases", "Room C", 40, 40, 0),
        )
    }

    @Test
    fun `paginates with zero-based pages and a filtered-independent total`() {
        val first = http.tree(http.get("/api/sessions?page=0&size=2"))
        assertThat(first["items"].values().map { it["id"].asLong() }).containsExactly(1L, 2L)
        assertThat(first["totalElements"].asLong()).isEqualTo(3)

        val second = http.tree(http.get("/api/sessions?page=1&size=2"))
        assertThat(second["items"].values().map { it["id"].asLong() }).containsExactly(3L)
        assertThat(second["page"].asInt()).isEqualTo(1)

        val beyond = http.tree(http.get("/api/sessions?page=5&size=2"))
        assertThat(beyond["items"].size()).isZero()
        assertThat(beyond["totalElements"].asLong()).isEqualTo(3)
    }

    @Test
    fun `defaults to page 0 and size 20`() {
        val page = http.tree(http.get("/api/sessions"))
        assertThat(page["page"].asInt()).isEqualTo(0)
        assertThat(page["size"].asInt()).isEqualTo(20)
    }

    @ParameterizedTest
    @ValueSource(strings = ["page=-1", "page=abc", "size=0", "size=101", "size=1.5", "page=0&size=x"])
    fun `rejects invalid paging with 400 VALIDATION_ERROR`(query: String) {
        val response = http.get("/api/sessions?$query")
        assertThat(response.statusCode.value()).isEqualTo(400)
        val error = http.tree(response)
        assertThat(error["code"].asText()).isEqualTo("VALIDATION_ERROR")
        assertThat(error["message"].asText()).isNotBlank()
        assertThat(error["status"].asInt()).isEqualTo(400)
    }

    @Test
    fun `unknown paths return the JSON error shape`() {
        val response = http.get("/api/nothing-here")
        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(http.tree(response)["code"].asText()).isEqualTo("NOT_FOUND")
    }
}
