package com.conference.planner

import com.conference.planner.demo.SeedResetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class AvailableOnlyIT : PostgresIntegrationTest() {
    @LocalServerPort private var port: Int = 0
    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var seedReset: SeedResetService

    @BeforeEach
    fun reset() {
        seedReset.reset()
    }

    @Test
    fun `availableOnly filters before pagination`() {
        val all = rest.getForEntity("http://localhost:$port/api/sessions?page=0&size=20", Map::class.java)
        assertThat(all.body!!["totalElements"]).isEqualTo(3)

        val filtered = rest.getForEntity(
            "http://localhost:$port/api/sessions?page=0&size=20&availableOnly=true",
            Map::class.java,
        )
        assertThat(filtered.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(filtered.body!!["totalElements"]).isEqualTo(2)
        @Suppress("UNCHECKED_CAST")
        val items = filtered.body!!["items"] as List<Map<String, Any>>
        assertThat(items).hasSize(2)
        assertThat(items.map { it["title"] }).doesNotContain("Understanding Databases")

        val bad = rest.getForEntity(
            "http://localhost:$port/api/sessions?availableOnly=yes",
            Map::class.java,
        )
        assertThat(bad.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(bad.body!!["code"]).isEqualTo("INVALID_AVAILABLE_ONLY")
    }

    @Test
    fun `cancellation restores availability in filter`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Demo-Attendee"] = "1"
        headers["Idempotency-Key"] = "free-one"
        // Book then cancel on session 2 (one place) — still available; cancel a seed booking via SQL-free path:
        // Book session 1, ensure filter still shows 2; cancel full session by booking after freeing:
        // Free a place on full session 3 by canceling is not possible for Alice without a booking.
        // Instead: book session 2 as Alice, then availableOnly still 2; cancel; still 2.
        rest.exchange(
            "http://localhost:$port/api/bookings",
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 2), headers),
            Map::class.java,
        )
        val afterBook = rest.getForEntity(
            "http://localhost:$port/api/sessions?availableOnly=true",
            Map::class.java,
        )
        assertThat(afterBook.body!!["totalElements"]).isEqualTo(1)

        @Suppress("UNCHECKED_CAST")
        val booking = rest.exchange(
            "http://localhost:$port/api/bookings",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            List::class.java,
        ).body!![0] as Map<*, *>
        val id = (booking["id"] as Number).toLong()
        rest.exchange(
            "http://localhost:$port/api/bookings/$id",
            HttpMethod.DELETE,
            HttpEntity<Void>(headers),
            Void::class.java,
        )
        val afterCancel = rest.getForEntity(
            "http://localhost:$port/api/sessions?availableOnly=true",
            Map::class.java,
        )
        assertThat(afterCancel.body!!["totalElements"]).isEqualTo(2)
    }
}
