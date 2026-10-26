package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.ResponseEntity
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/**
 * Presenter reproducer for exercise 2. It coordinates two booking requests so that both
 * pass the availability count before either insert is attempted, using a test-only
 * JDBC proxy: no instrumentation exists in the application's default runtime.
 *
 * The wait is bounded by an explicit timeout. A correctly serialised implementation
 * simply has one request blocked on the session row lock and never delivers a second
 * arrival, so this design cannot deadlock the fixed code.
 *
 * Expected: FAILS on the faulty snapshot, PASSES after presenter/reference-fixes/e2.patch.
 */
class OverbookingReproducerIT : PostgresIntegrationTest() {

    @TestConfiguration
    class CoordinationConfig {

        @Bean
        fun countBarrier(): CountBarrier = CountBarrier()

        @Bean
        fun dataSourceWrapper(barrier: CountBarrier): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (bean is DataSource && beanName == "dataSource") {
                    return wrapDataSource(bean, barrier)
                }
                return bean
            }
        }

        private fun wrapDataSource(dataSource: DataSource, barrier: CountBarrier): DataSource =
            java.lang.reflect.Proxy.newProxyInstance(
                DataSource::class.java.classLoader,
                arrayOf(DataSource::class.java),
            ) { _, method, args ->
                if (method.name == "getConnection" && (args == null || args.isEmpty())) {
                    wrapConnection(dataSource.connection, barrier)
                } else {
                    method.invoke(dataSource, *(args ?: emptyArray()))
                }
            } as DataSource

        private fun wrapConnection(connection: Connection, barrier: CountBarrier): Connection =
            java.lang.reflect.Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, args ->
                if (method.name == "prepareStatement") {
                    val callArgs = args ?: emptyArray()
                    val sql = callArgs[0] as String
                    val statement = if (callArgs.size == 1) {
                        connection.prepareStatement(sql)
                    } else {
                        connection.prepareStatement(sql, callArgs[1] as Int)
                    }
                    wrapStatement(statement, barrier, sql)
                } else {
                    method.invoke(connection, *(args ?: emptyArray()))
                }
            } as Connection

        private fun wrapStatement(
            statement: PreparedStatement,
            barrier: CountBarrier,
            sql: String,
        ): PreparedStatement =
            java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, args ->
                if (method.name == "executeQuery" || method.name == "execute") {
                    barrier.awaitAfterAvailabilityCheck(sql)
                }
                method.invoke(statement, *(args ?: emptyArray()))
            } as PreparedStatement
    }

    class CountBarrier {
        private val latch = AtomicReference<CountDownLatch?>(null)
        private val used = ThreadLocal.withInitial { false }

        fun arm() {
            used.set(false)
            latch.set(CountDownLatch(2))
        }

        fun awaitAfterAvailabilityCheck(sql: String) {
            if (!sql.contains("count(*) from bookings")) return
            val current = latch.get() ?: return
            if (used.get()) return
            used.set(true)
            current.countDown()
            current.await(3, TimeUnit.SECONDS)
        }
    }

    @Autowired
    lateinit var barrier: CountBarrier

    @BeforeEach
    fun seed() = resetDemo()

    @Test
    @Timeout(60)
    fun `two attendees cannot both take the last place`() {
        barrier.arm()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val startGate = CountDownLatch(1)
            val racers = listOf("Racer A" to newKey(), "Racer B" to newKey())
            val futures = racers.map { (attendee, key) ->
                pool.submit<ResponseEntity<String>> {
                    startGate.await(5, TimeUnit.SECONDS)
                    postBooking(attendee, key, sessionId = 2)
                }
            }
            startGate.countDown()
            val responses = futures.map { it.get(30, TimeUnit.SECONDS) }

            val created = responses.count { it.statusCode.value() == 201 }
            val full = responses.count { it.statusCode.value() == 409 && errorCode(it) == "SESSION_FULL" }
            assertThat(created).describedAs("exactly one attendee wins the last place").isEqualTo(1)
            assertThat(full).isEqualTo(1)

            val session = json(getResponse("/api/sessions"))["items"][1]
            assertThat(session.path("activeBookings").asInt()).isEqualTo(20)
            assertThat(session.path("availablePlaces").asInt()).isEqualTo(0)
        } finally {
            pool.shutdownNow()
        }
    }
}
