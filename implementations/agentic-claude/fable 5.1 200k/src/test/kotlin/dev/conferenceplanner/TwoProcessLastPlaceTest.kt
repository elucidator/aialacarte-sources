package dev.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import tools.jackson.databind.json.JsonMapper

/**
 * Two separate JVM processes (the packaged jar) share one PostgreSQL. Simultaneous bookers spread
 * over both processes race for the last place. Run with: ./gradlew twoProcessTest
 */
@Tag("two-process")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TwoProcessLastPlaceTest {

    private val json = JsonMapper.builder().build()
    private val processes = mutableListOf<Process>()
    private lateinit var apps: List<Http>

    @BeforeAll
    fun startTwoProcesses() {
        val jar = System.getProperty("conference.jar") ?: error("conference.jar system property missing; run via ./gradlew twoProcessTest")
        val java = ProcessHandle.current().info().command().orElse("java")
        val logDir = File("build/two-process-logs").apply { mkdirs() }
        val ports = List(2) { ServerSocket(0).use { it.localPort } }
        val db = TestDatabase.postgres
        apps = ports.mapIndexed { i, port ->
            val process = ProcessBuilder(
                java, "-jar", jar,
                "--server.port=$port",
                "--spring.profiles.active=local-demo",
                "--spring.datasource.url=${db.jdbcUrl}",
                "--spring.datasource.username=${db.username}",
                "--spring.datasource.password=${db.password}",
            ).redirectErrorStream(true).redirectOutput(File(logDir, "app-$i.log")).start()
            processes += process
            Http("http://localhost:$port", json)
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        for (app in apps) {
            while (runCatching { app.get("/health").statusCode.value() }.getOrDefault(0) != 200) {
                check(System.nanoTime() < deadline) { "app did not become healthy within 90s; see $logDir" }
                check(processes.all { it.isAlive }) { "an app process exited early; see $logDir" }
                Thread.sleep(250)
            }
        }
    }

    @AfterAll
    fun stopProcesses() {
        processes.forEach { it.destroy() }
        processes.forEach { if (!it.waitFor(10, TimeUnit.SECONDS)) it.destroyForcibly() }
    }

    @Test
    fun `simultaneous bookers on two processes never overbook the last place`() {
        repeat(3) { round ->
            assertThat(apps[0].post("/api/demo/reset").statusCode.value()).isEqualTo(200)
            val attendees = (20..31).map { "seed-%02d".format(it) }
            val pool = Executors.newFixedThreadPool(attendees.size)
            val start = CountDownLatch(1)
            val responses = try {
                val futures = attendees.mapIndexed { i, attendee ->
                    pool.submit<Int> { start.await(); apps[i % 2].book(attendee, 2, UUID.randomUUID().toString()).statusCode.value() }
                }
                start.countDown()
                futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }
            val counts = responses.groupingBy { it }.eachCount()
            println("round $round: $counts")
            assertThat(counts).describedAs("round $round").isEqualTo(mapOf(201 to 1, 409 to attendees.size - 1))
            for (app in apps) {
                assertThat(app.session(2)["activeBookings"].asInt()).isEqualTo(20)
                assertThat(app.session(2)["availablePlaces"].asInt()).isEqualTo(0)
            }
        }
    }
}
