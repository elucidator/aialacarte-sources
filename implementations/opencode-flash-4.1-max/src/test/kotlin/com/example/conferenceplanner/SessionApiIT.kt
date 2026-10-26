package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class SessionApiIT : PostgresIntegrationTest() {

    @BeforeEach
    fun seed() = resetDemo()

    @Test
    fun `lists the three seed sessions in stable id order with derived availability`() {
        val response = getResponse("/api/sessions?page=0&size=20")
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val body = json(response)
        assertThat(body.path("page").asInt()).isEqualTo(0)
        assertThat(body.path("size").asInt()).isEqualTo(20)
        assertThat(body.path("totalElements").asLong()).isEqualTo(3)
        assertThat(body.path("items")).hasSize(3)

        val first = body.path("items")[0]
        assertThat(first.path("id").asLong()).isEqualTo(1)
        assertThat(first.path("title").asText()).isEqualTo("Debugging Distributed Systems")
        assertThat(first.path("room").asText()).isEqualTo("Room A")
        assertThat(first.path("capacity").asInt()).isEqualTo(30)
        assertThat(first.path("activeBookings").asInt()).isEqualTo(22)
        assertThat(first.path("availablePlaces").asInt()).isEqualTo(8)

        val second = body.path("items")[1]
        assertThat(second.path("id").asLong()).isEqualTo(2)
        assertThat(second.path("title").asText()).isEqualTo("Practical Kotlin")
        assertThat(second.path("activeBookings").asInt()).isEqualTo(19)
        assertThat(second.path("availablePlaces").asInt()).isEqualTo(1)

        val third = body.path("items")[2]
        assertThat(third.path("id").asLong()).isEqualTo(3)
        assertThat(third.path("title").asText()).isEqualTo("Understanding Databases")
        assertThat(third.path("activeBookings").asInt()).isEqualTo(40)
        assertThat(third.path("availablePlaces").asInt()).isEqualTo(0)
    }

    @Test
    fun `paginates with zero-based page and reports the full total`() {
        val body = json(getResponse("/api/sessions?page=1&size=2"))
        assertThat(body.path("page").asInt()).isEqualTo(1)
        assertThat(body.path("totalElements").asLong()).isEqualTo(3)
        assertThat(body.path("items")).hasSize(1)
        assertThat(body.path("items")[0].path("id").asLong()).isEqualTo(3)
    }

    @Test
    fun `rejects negative page with 400`() {
        val response = getResponse("/api/sessions?page=-1&size=20")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(response)).isEqualTo("INVALID_PAGE")
    }

    @Test
    fun `rejects size outside 1 to 100 with 400`() {
        assertThat(getResponse("/api/sessions?page=0&size=0").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(getResponse("/api/sessions?page=0&size=0"))).isEqualTo("INVALID_SIZE")
        assertThat(getResponse("/api/sessions?page=0&size=101").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(getResponse("/api/sessions?page=0&size=101"))).isEqualTo("INVALID_SIZE")
    }

    @Test
    fun `rejects non-numeric pagination values with 400`() {
        val response = getResponse("/api/sessions?page=abc&size=20")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode(response)).isEqualTo("INVALID_PARAMETER")
    }
}
