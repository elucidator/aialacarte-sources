package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class HealthIT : PostgresIntegrationTest() {

    @Test
    fun `health returns 200 when app and database are ready`() {
        val response = rest.getForEntity("/health", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(json(response).path("status").asText()).isEqualTo("UP")
    }
}
