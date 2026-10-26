package com.conference.planner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Starts two app processes against one PostgreSQL database and books the last place
 * concurrently. Enable with RUN_TWO_PROCESS_IT=true (requires a prior bootJar).
 */
@Testcontainers
class TwoProcessCapacityIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("conference")
            .withUsername("conference")
            .withPassword("conference")
    }

    @Test
    @Timeout(120)
    @EnabledIfEnvironmentVariable(named = "RUN_TWO_PROCESS_IT", matches = "true")
    fun `two processes cannot overbook the last place`() {
        val projectDir = File(System.getProperty("user.dir"))
        val jars = File(projectDir, "build/libs").listFiles { f -> f.name.endsWith(".jar") && !f.name.contains("plain") }
            ?: error("No boot jar found; run ./gradlew bootJar first")
        val jar = jars.single()

        val portA = 18081
        val portB = 18082
        val envBase = mapOf(
            "DB_HOST" to postgres.host,
            "DB_PORT" to postgres.firstMappedPort.toString(),
            "DB_NAME" to "conference",
            "DB_USER" to "conference",
            "DB_PASSWORD" to "conference",
            "SPRING_PROFILES_ACTIVE" to "local-demo",
        )

        val procA = startApp(jar, portA, envBase)
        val procB = startApp(jar, portB, envBase)
        try {
            waitForHealth(portA)
            waitForHealth(portB)

            // Reset via process A
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            post(client, "http://localhost:$portA/api/demo/reset", "{}")

            val created = AtomicInteger()
            val conflicts = AtomicInteger()
            val pool = Executors.newFixedThreadPool(2)
            val futures = pool.invokeAll(
                listOf(
                    Callable { book(client, portA, 1, "alice-2p") },
                    Callable { book(client, portB, 2, "bob-2p") },
                ),
                30,
                TimeUnit.SECONDS,
            )
            pool.shutdownNow()
            for (f in futures) {
                when (val code = f.get()) {
                    201 -> created.incrementAndGet()
                    409 -> conflicts.incrementAndGet()
                    else -> error("Unexpected status $code")
                }
            }
            assertThat(created.get()).isEqualTo(1)
            assertThat(conflicts.get()).isEqualTo(1)

            val sessions = get(client, "http://localhost:$portA/api/sessions?page=0&size=20")
            assertThat(sessions).contains("\"activeBookings\":20")
        } finally {
            procA.destroyForcibly()
            procB.destroyForcibly()
            procA.waitFor(10, TimeUnit.SECONDS)
            procB.waitFor(10, TimeUnit.SECONDS)
        }
    }

    private fun startApp(jar: File, port: Int, envBase: Map<String, String>): Process {
        val pb = ProcessBuilder(
            "java",
            "-jar",
            jar.absolutePath,
            "--server.port=$port",
        )
        pb.environment().putAll(envBase)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(File("build/two-process-$port.log")))
        return pb.start()
    }

    private fun waitForHealth(port: Int) {
        val client = HttpClient.newHttpClient()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (System.nanoTime() < deadline) {
            try {
                val code = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                ).statusCode()
                if (code == 200) return
            } catch (_: Exception) {
                // retry
            }
            Thread.sleep(500)
        }
        error("App on port $port did not become healthy")
    }

    private fun book(client: HttpClient, port: Int, attendeeId: Long, key: String): Int {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/bookings"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("X-Demo-Attendee", attendeeId.toString())
            .header("Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString("""{"sessionId":2}"""))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    private fun post(client: HttpClient, url: String, body: String) {
        client.send(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun get(client: HttpClient, url: String): String =
        client.send(
            HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body()
}
