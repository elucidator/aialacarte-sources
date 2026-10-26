package com.conference.planner

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
import com.conference.planner.demo.SeedResetService

class BookingApiIT : PostgresIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var seedReset: SeedResetService

    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun reset() {
        seedReset.reset()
    }

    @Test
    fun `lists sessions with seed counts and stable order`() {
        val res = rest.getForEntity(url("/api/sessions?page=0&size=20"), Map::class.java)
        assertThat(res.statusCode).isEqualTo(HttpStatus.OK)
        val body = res.body!!
        assertThat(body["page"]).isEqualTo(0)
        assertThat(body["size"]).isEqualTo(20)
        assertThat(body["totalElements"]).isEqualTo(3)
        @Suppress("UNCHECKED_CAST")
        val items = body["items"] as List<Map<String, Any>>
        assertThat(items).hasSize(3)
        assertThat(items[0]["title"]).isEqualTo("Debugging Distributed Systems")
        assertThat(items[0]["activeBookings"]).isEqualTo(22)
        assertThat(items[0]["availablePlaces"]).isEqualTo(8)
        assertThat(items[1]["title"]).isEqualTo("Practical Kotlin")
        assertThat(items[1]["activeBookings"]).isEqualTo(19)
        assertThat(items[1]["availablePlaces"]).isEqualTo(1)
        assertThat(items[2]["title"]).isEqualTo("Understanding Databases")
        assertThat(items[2]["activeBookings"]).isEqualTo(40)
        assertThat(items[2]["availablePlaces"]).isEqualTo(0)
    }

    @Test
    fun `rejects invalid pagination`() {
        val badPage = rest.getForEntity(url("/api/sessions?page=-1&size=20"), Map::class.java)
        assertThat(badPage.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(badPage.body!!["code"]).isEqualTo("INVALID_PAGE")

        val badSize = rest.getForEntity(url("/api/sessions?page=0&size=0"), Map::class.java)
        assertThat(badSize.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(badSize.body!!["code"]).isEqualTo("INVALID_SIZE")
    }

    @Test
    fun `books cancels and rejects full session`() {
        val headers = demoHeaders(1, "key-alice-1")
        val book = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(book.statusCode).isEqualTo(HttpStatus.CREATED)
        val bookingId = (book.body!!["id"] as Number).toLong()

        val list = rest.exchange(
            url("/api/bookings"),
            HttpMethod.GET,
            HttpEntity<Void>(demoHeaders(1)),
            List::class.java,
        )
        assertThat(list.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(list.body!!).hasSize(1)

        val cancel = rest.exchange(
            url("/api/bookings/$bookingId"),
            HttpMethod.DELETE,
            HttpEntity<Void>(demoHeaders(1)),
            Void::class.java,
        )
        assertThat(cancel.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        val cancelAgain = rest.exchange(
            url("/api/bookings/$bookingId"),
            HttpMethod.DELETE,
            HttpEntity<Void>(demoHeaders(1)),
            Void::class.java,
        )
        assertThat(cancelAgain.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        val full = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 3), demoHeaders(1, "key-full")),
            Map::class.java,
        )
        assertThat(full.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(full.body!!["code"]).isEqualTo("SESSION_FULL")
    }

    @Test
    fun `idempotent replay returns same booking and cancel does not rebook on replay`() {
        val headers = demoHeaders(1, "stable-key")
        val first = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)
        val id = (first.body!!["id"] as Number).toLong()

        val replay = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(replay.statusCode).isEqualTo(HttpStatus.OK)
        assertThat((replay.body!!["id"] as Number).toLong()).isEqualTo(id)

        rest.exchange(
            url("/api/bookings/$id"),
            HttpMethod.DELETE,
            HttpEntity<Void>(demoHeaders(1)),
            Void::class.java,
        )

        val afterCancel = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(afterCancel.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(afterCancel.body!!["status"]).isEqualTo("CANCELLED")
        assertThat((afterCancel.body!!["id"] as Number).toLong()).isEqualTo(id)

        val sessions = rest.getForEntity(url("/api/sessions?page=0&size=20"), Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = sessions.body!!["items"] as List<Map<String, Any>>
        assertThat(items[0]["activeBookings"]).isEqualTo(22)
    }

    @Test
    fun `duplicate active booking and key reuse with different payload conflict`() {
        rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), demoHeaders(1, "k1")),
            Map::class.java,
        )
        val dup = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), demoHeaders(1, "k2")),
            Map::class.java,
        )
        assertThat(dup.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(dup.body!!["code"]).isEqualTo("DUPLICATE_ACTIVE_BOOKING")

        val reuse = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 2), demoHeaders(1, "k1")),
            Map::class.java,
        )
        assertThat(reuse.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(reuse.body!!["code"]).isEqualTo("IDEMPOTENCY_KEY_REUSED")
    }

    @Test
    fun `cancel other attendees booking is 404`() {
        val created = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), demoHeaders(1, "alice-only")),
            Map::class.java,
        )
        val id = (created.body!!["id"] as Number).toLong()
        val res = rest.exchange(
            url("/api/bookings/$id"),
            HttpMethod.DELETE,
            HttpEntity<Void>(demoHeaders(2)),
            Map::class.java,
        )
        assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `health is up`() {
        val res = rest.getForEntity(url("/health"), Map::class.java)
        assertThat(res.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(res.body!!["status"]).isEqualTo("UP")
    }

    @Test
    fun `missing identity returns 400`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["Idempotency-Key"] = "x"
        val res = rest.exchange(
            url("/api/bookings"),
            HttpMethod.POST,
            HttpEntity(mapOf("sessionId" to 1), headers),
            Map::class.java,
        )
        assertThat(res.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(res.body!!["code"]).isEqualTo("MISSING_ATTENDEE")
    }

    private fun demoHeaders(attendeeId: Long, idempotencyKey: String? = null): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Demo-Attendee"] = attendeeId.toString()
        if (idempotencyKey != null) {
            headers["Idempotency-Key"] = idempotencyKey
        }
        return headers
    }
}
