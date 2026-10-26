package dev.conferenceplanner

import dev.conferenceplanner.api.HealthController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource

class HealthTest : IntegrationTestBase() {

    @Test
    fun `health is 200 UP when the database answers`() {
        val response = http.get("/health")
        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(http.tree(response)["status"].asText()).isEqualTo("UP")
        assertThat(http.tree(response)["database"].asText()).isEqualTo("UP")
    }

    @Test
    fun `health is 503 DOWN when the database is unreachable`() {
        val dead = DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/nowhere?connectTimeout=1&socketTimeout=1", "x", "x")
        val response = HealthController(JdbcClient.create(dead)).health()
        assertThat(response.statusCode.value()).isEqualTo(503)
        assertThat(response.body!!["status"]).isEqualTo("DOWN")
    }
}
