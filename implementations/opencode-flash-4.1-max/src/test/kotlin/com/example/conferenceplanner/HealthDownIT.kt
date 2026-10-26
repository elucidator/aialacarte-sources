package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

/**
 * The database is unreachable on purpose: /health must fail fast with a non-success
 * status instead of hanging or reporting healthy.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.flyway.enabled=false",
    ],
)
@ActiveProfiles("local-demo")
class HealthDownIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `health returns 503 when the database is down`() {
        val response = rest.getForEntity("/health", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body).contains("DOWN")
    }
}
