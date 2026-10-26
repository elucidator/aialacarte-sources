package com.example.conferenceplanner

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-demo")
abstract class PostgresIntegrationTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", TestDatabase.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestDatabase.postgres::getUsername)
            registry.add("spring.datasource.password", TestDatabase.postgres::getPassword)
        }
    }

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected fun resetDemo() {
        val response = rest.postForEntity("/api/demo/reset", null, String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    protected fun getJson(path: String, attendee: String? = null): JsonNode =
        json(exchange(HttpMethod.GET, path, attendee = attendee))

    protected fun getResponse(path: String, attendee: String? = null): ResponseEntity<String> =
        exchange(HttpMethod.GET, path, attendee = attendee)

    protected fun postBooking(
        attendee: String?,
        requestKey: String?,
        sessionId: Long,
        body: String? = """{"sessionId":$sessionId}""",
    ): ResponseEntity<String> =
        exchange(
            HttpMethod.POST,
            "/api/bookings",
            attendee = attendee,
            requestKey = requestKey,
            body = body,
        )

    protected fun cancelBooking(attendee: String?, bookingId: Long): ResponseEntity<String> =
        exchange(HttpMethod.DELETE, "/api/bookings/$bookingId", attendee = attendee)

    protected fun exchange(
        method: HttpMethod,
        path: String,
        attendee: String? = null,
        requestKey: String? = null,
        body: String? = null,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        if (attendee != null) headers.add("X-Demo-Attendee", attendee)
        if (requestKey != null) headers.add("Idempotency-Key", requestKey)
        if (body != null) headers.contentType = MediaType.APPLICATION_JSON
        return rest.exchange(path, method, HttpEntity(body, headers), String::class.java)
    }

    protected fun json(response: ResponseEntity<String>): JsonNode {
        assertThat(response.body).describedAs("response body").isNotNull
        return objectMapper.readTree(response.body)
    }

    protected fun errorCode(response: ResponseEntity<String>): String =
        json(response).path("code").asText()

    protected fun newKey(): String = UUID.randomUUID().toString()
}
