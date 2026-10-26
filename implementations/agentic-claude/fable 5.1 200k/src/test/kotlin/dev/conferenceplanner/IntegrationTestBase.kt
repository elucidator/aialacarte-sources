package dev.conferenceplanner

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/** One PostgreSQL container for the whole test JVM; every test starts from the seed state. */
object TestDatabase {
    val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17.9-alpine").also { it.start() }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["spring.profiles.active=local-demo"])
abstract class IntegrationTestBase {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { TestDatabase.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestDatabase.postgres.username }
            registry.add("spring.datasource.password") { TestDatabase.postgres.password }
        }
    }

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var json: ObjectMapper

    protected val http: Http by lazy { Http("http://localhost:$port", json) }

    @BeforeEach
    fun resetSeed() {
        check(http.post("/api/demo/reset").statusCode.value() == 200) { "seed reset failed" }
    }
}

/** Small HTTP helper that never throws on 4xx/5xx so tests can assert status and body together. */
class Http(baseUrl: String, private val json: ObjectMapper) {
    private val client = RestClient.builder().baseUrl(baseUrl).defaultStatusHandler({ true }) { _, _ -> }.build()

    fun get(path: String, attendee: String? = null): ResponseEntity<String> =
        client.get().uri(path).headers { if (attendee != null) it.set("X-Demo-Attendee", attendee) }.retrieve().toEntity(String::class.java)

    fun post(path: String, body: String? = null, attendee: String? = null, requestKey: String? = null, contentType: String = "application/json"): ResponseEntity<String> =
        client.post().uri(path)
            .headers {
                if (attendee != null) it.set("X-Demo-Attendee", attendee)
                if (requestKey != null) it.set("Idempotency-Key", requestKey)
                if (body != null) it.set("Content-Type", contentType)
            }
            .apply { if (body != null) body(body) }
            .retrieve().toEntity(String::class.java)

    fun delete(path: String, attendee: String? = null): ResponseEntity<String> =
        client.delete().uri(path).headers { if (attendee != null) it.set("X-Demo-Attendee", attendee) }.retrieve().toEntity(String::class.java)

    fun book(attendee: String, sessionId: Long, requestKey: String): ResponseEntity<String> =
        post("/api/bookings", """{"sessionId": $sessionId}""", attendee, requestKey)

    fun tree(response: ResponseEntity<String>): JsonNode = json.readTree(response.body ?: "null")

    fun session(id: Long): JsonNode = tree(get("/api/sessions?page=0&size=20"))["items"].first { it["id"].asLong() == id }

    fun activity(): JsonNode = tree(get("/api/demo/activity"))
}
