package conference

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-demo")
class PlannerIntegrationTest {
    @Autowired lateinit var service: BookingService
    @Autowired lateinit var demo: DemoController
    @Autowired lateinit var dispatcher: ConfirmationDispatcher
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mvc: MockMvc
    @BeforeEach fun reset() { demo.reset() }
    private fun book(who: String = "alice", key: String = "one", session: Long = 1) = service.book(who, key, BookingInput(session))
    private fun problem(code: String, action: () -> Unit) {
        val e = assertThrows(ApiProblem::class.java, action); assertEquals(code, e.code)
    }
    private fun <T> simultaneous(actions: List<() -> T>): List<T> {
        val barrier = CyclicBarrier(actions.size)
        val pool = Executors.newFixedThreadPool(actions.size)
        try { return pool.invokeAll(actions.map { action -> Callable { barrier.await(5, TimeUnit.SECONDS); action() } },
            15, TimeUnit.SECONDS).map { it.get(1, TimeUnit.SECONDS) } }
        finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }
    @Test fun `seed and pagination have stable order and totals`() {
        val all = service.sessions(0, 20)
        assertEquals(listOf(1L, 2L, 3L), all.items.map { it.id })
        assertEquals(listOf(22, 19, 40), all.items.map { it.activeBookings })
        assertEquals(listOf(8, 1, 0), all.items.map { it.availablePlaces })
        assertEquals(3L, all.totalElements)
        assertEquals(2L, service.sessions(1, 1).items.single().id)
        assertEquals(3L, service.sessions(Int.MAX_VALUE, 100).totalElements)
        assertTrue(service.sessions(9, 20).items.isEmpty())
        problem("INVALID_INPUT") { service.sessions(-1, 20) }
        problem("INVALID_INPUT") { service.sessions(0, 101) }
    }
    @Test fun `book replay duplicate and cancellation semantics`() {
        val first = book(); assertFalse(first.replay)
        assertEquals(first.booking, book().booking); assertTrue(book().replay)
        problem("KEY_REUSED") { book(session=2) }
        problem("ALREADY_BOOKED") { book(key="different") }
        problem("SESSION_FULL") { book("bob", "full", 3) }
        problem("NOT_FOUND") { book(key="unknown", session=999) }
        problem("NOT_FOUND") { service.cancel("bob", first.booking.id) }
        problem("NOT_FOUND") { service.cancel("alice", "missing") }
        service.cancel("alice", first.booking.id); service.cancel("alice", first.booking.id)
        assertEquals(22, service.sessions(0,20).items.first().activeBookings)
        assertTrue(service.bookings("alice").isEmpty())
        assertEquals("CANCELLED", book().booking.status)
        assertEquals(22, service.sessions(0,20).items.first().activeBookings)
        assertNotEquals(first.booking.id, book(key="fresh").booking.id)
    }
    @Test fun `concurrent requests preserve capacity and replay`() {
        val last = simultaneous(listOf("alice", "bob").map { who -> {
            try { book(who, "last", 2); 201 } catch (e: ApiProblem) { e.status.value() }
        } })
        assertEquals(listOf(201,409), last.sorted())
        assertEquals(20, service.sessions(0,20).items[1].activeBookings)
        val replay = simultaneous((1..6).map { { book("alice", "repeat", 1) } })
        assertEquals(1, replay.count { !it.replay })
        assertEquals(1, replay.map { it.booking.id }.distinct().size)
        val duplicates = simultaneous((1..4).map { n -> {
            try { book("bob", "distinct-$n", 1); 201 } catch (e: ApiProblem) { e.status.value() }
        } })
        assertEquals(1, duplicates.count { it == 201 })
        val id = replay.first().booking.id
        simultaneous((1..4).map { { service.cancel("alice", id) } })
        assertEquals(23, service.sessions(0,20).items.first().activeBookings)
    }
    @Test fun `gateway acceptance survives lost acknowledgement and retry deduplicates`() {
        val booking = book().booking
        demo.mode(ModeInput("fail")); dispatcher.dispatch()
        assertEquals("ACTIVE", service.bookings("alice").single().status)
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM gateway_messages", Long::class.java)!!)
        demo.mode(ModeInput("lost-ack")); dispatcher.dispatch()
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM gateway_messages", Long::class.java)!!)
        assertEquals(false, jdbc.queryForObject("SELECT acknowledged FROM confirmations WHERE booking_id=?", Boolean::class.java, booking.id))
        demo.mode(ModeInput("normal")); dispatcher.dispatch(); dispatcher.dispatch()
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM gateway_messages", Long::class.java)!!)
        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM gateway_attempts", Long::class.java)!!)
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM activity WHERE kind='ACKNOWLEDGED'", Long::class.java)!!)
        demo.reset()
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM confirmations", Long::class.java)!!)
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM gateway_attempts", Long::class.java)!!)
    }
    @Test fun `health reports unavailable when a required table cannot be queried`() {
        jdbc.execute("ALTER TABLE sessions RENAME TO sessions_health_check")
        try { mvc.perform(get("/health")).andExpect(status().isServiceUnavailable) }
        finally { jdbc.execute("ALTER TABLE sessions_health_check RENAME TO sessions") }
        mvc.perform(get("/health")).andExpect(status().isOk)
    }
    @Test fun `HTTP contract validates input and returns consistent errors`() {
        mvc.perform(get("/health")).andExpect(status().isOk)
        for (query in listOf("page=-1", "size=0", "size=101", "page=bad", "page=", "size="))
            mvc.perform(get("/api/sessions?$query")).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        mvc.perform(post("/api/bookings").contentType("application/json").content("{\"sessionId\":1}"))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/api/bookings").header("X-Demo-Attendee","alice").header("Idempotency-Key","http")
            .contentType("application/json").content("{\"sessionId\":1}"))
            .andExpect(status().isCreated).andExpect(jsonPath("$.status").value("ACTIVE"))
        mvc.perform(post("/api/bookings").header("X-Demo-Attendee","alice").header("Idempotency-Key","http")
            .contentType("application/json").content("{\"sessionId\":1}"))
            .andExpect(status().isOk)
        mvc.perform(post("/api/bookings").header("X-Demo-Attendee","alice").header("Idempotency-Key","other")
            .contentType("application/json").content("{\"sessionId\":3}"))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SESSION_FULL"))
        mvc.perform(post("/api/bookings").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest)
        for (body in listOf("{\"sessionId\":1.5}", "{\"sessionId\":\"1\"}", "{\"sessionId\":null}", "[]"))
            mvc.perform(post("/api/bookings").header("X-Demo-Attendee","bob").header("Idempotency-Key","malformed")
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        mvc.perform(post("/api/bookings").header("X-Demo-Attendee","bob")
            .contentType("application/json").content("{\"sessionId\":1}"))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/bookings")).andExpect(status().isBadRequest)
    }
}
