package dev.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper

/** Without the local-demo profile the demo endpoints do not exist at all. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoProfileTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { TestDatabase.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestDatabase.postgres.username }
            registry.add("spring.datasource.password") { TestDatabase.postgres.password }
        }
    }

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var json: ObjectMapper

    @Test
    fun `demo endpoints are 404 outside the local-demo profile while the app works`() {
        val http = Http("http://localhost:$port", json)
        assertThat(http.get("/api/sessions").statusCode.value()).isEqualTo(200)
        assertThat(http.get("/health").statusCode.value()).isEqualTo(200)
        for (path in listOf("/api/demo/status", "/api/demo/activity")) {
            val response = http.get(path)
            assertThat(response.statusCode.value()).describedAs(path).isEqualTo(404)
            assertThat(http.tree(response)["code"].asText()).isEqualTo("NOT_FOUND")
        }
        for (path in listOf("/api/demo/reset", "/api/demo/dispatch")) {
            assertThat(http.post(path).statusCode.value()).describedAs(path).isEqualTo(404)
        }
        assertThat(http.post("/api/demo/gateway", """{"loseAcknowledgements": true}""").statusCode.value()).isEqualTo(404)
    }
}
