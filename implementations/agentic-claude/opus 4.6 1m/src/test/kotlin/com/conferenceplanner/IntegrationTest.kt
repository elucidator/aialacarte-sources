package com.conferenceplanner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local-demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("conference_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }

        val SESSION_1 = "00000000-0000-0000-0000-000000000001"
        val SESSION_2 = "00000000-0000-0000-0000-000000000002"
        val SESSION_3 = "00000000-0000-0000-0000-000000000003"
    }

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    @BeforeEach
    fun resetSeed() {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isOk)
    }

    @Test
    fun `health endpoint returns UP`() {
        mvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `list sessions returns seed data in stable order`() {
        val result = mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk)
            .andReturn()
        val body = mapper.readValue<Map<String, Any>>(result.response.contentAsString)
        val items = body["items"] as List<*>
        assertEquals(3, items.size)
        assertEquals(3L, (body["totalElements"] as Number).toLong())

        @Suppress("UNCHECKED_CAST")
        val first = items[0] as Map<String, Any>
        assertEquals("Debugging Distributed Systems", first["title"])
        assertEquals(30, (first["capacity"] as Number).toInt())
        assertEquals(22, (first["activeBookings"] as Number).toInt())
        assertEquals(8, (first["availablePlaces"] as Number).toInt())

        @Suppress("UNCHECKED_CAST")
        val third = items[2] as Map<String, Any>
        assertEquals("Understanding Databases", third["title"])
        assertEquals(40, (third["activeBookings"] as Number).toInt())
        assertEquals(0, (third["availablePlaces"] as Number).toInt())
    }

    @Test
    fun `pagination works`() {
        val result = mvc.perform(get("/api/sessions?page=0&size=1"))
            .andExpect(status().isOk)
            .andReturn()
        val body = mapper.readValue<Map<String, Any>>(result.response.contentAsString)
        val items = body["items"] as List<*>
        assertEquals(1, items.size)
        assertEquals(3L, (body["totalElements"] as Number).toLong())
    }

    @Test
    fun `invalid page returns 400`() {
        mvc.perform(get("/api/sessions?page=-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PAGE"))
    }

    @Test
    fun `invalid size returns 400`() {
        mvc.perform(get("/api/sessions?size=0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SIZE"))

        mvc.perform(get("/api/sessions?size=101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
    }

    @Test
    fun `create booking returns 201`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-create-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.attendee").value("alice"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `replay booking returns 200 with same data`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-replay-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-replay-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.attendee").value("alice"))
    }

    @Test
    fun `key conflict returns 409`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-conflict-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "bob")
            .header("Idempotency-Key", "key-conflict-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("KEY_CONFLICT"))
    }

    @Test
    fun `full session returns 409`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-full-1")
            .content("""{"sessionId":"$SESSION_3"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_FULL"))
    }

    @Test
    fun `duplicate active booking returns 409`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-dup-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-dup-2")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_BOOKED"))
    }

    @Test
    fun `unknown session returns 404`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-unknown-1")
            .content("""{"sessionId":"${UUID.randomUUID()}"}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
    }

    @Test
    fun `missing headers return 400`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list bookings for attendee`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-list-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        val result = mvc.perform(get("/api/bookings")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isOk)
            .andReturn()
        val bookings = mapper.readValue<List<Map<String, Any>>>(result.response.contentAsString)
        assertEquals(1, bookings.size)
        assertEquals("alice", bookings[0]["attendee"])
    }

    @Test
    fun `cancel booking returns 204`() {
        val createResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-cancel-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)
            .andReturn()
        val booking = mapper.readValue<Map<String, Any>>(createResult.response.contentAsString)
        val bookingId = booking["id"]

        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `re-cancel returns 204 idempotently`() {
        val createResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-recancel-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)
            .andReturn()
        val booking = mapper.readValue<Map<String, Any>>(createResult.response.contentAsString)
        val bookingId = booking["id"]

        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isNoContent)

        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `cancel another attendees booking returns 404`() {
        val createResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-other-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)
            .andReturn()
        val booking = mapper.readValue<Map<String, Any>>(createResult.response.contentAsString)
        val bookingId = booking["id"]

        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "bob"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `cancellation frees a place`() {
        // Session 2 has 19/20 booked, 1 available
        val createResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-free-1")
            .content("""{"sessionId":"$SESSION_2"}"""))
            .andExpect(status().isCreated)
            .andReturn()
        val booking = mapper.readValue<Map<String, Any>>(createResult.response.contentAsString)
        val bookingId = booking["id"]

        // Now full (20/20)
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "bob")
            .header("Idempotency-Key", "key-free-2")
            .content("""{"sessionId":"$SESSION_2"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_FULL"))

        // Cancel alice's booking
        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isNoContent)

        // Now bob can book
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "bob")
            .header("Idempotency-Key", "key-free-3")
            .content("""{"sessionId":"$SESSION_2"}"""))
            .andExpect(status().isCreated)
    }

    @Test
    fun `replay after cancellation returns original booking state`() {
        val createResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-replay-cancel-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)
            .andReturn()
        val booking = mapper.readValue<Map<String, Any>>(createResult.response.contentAsString)
        val bookingId = booking["id"]

        mvc.perform(delete("/api/bookings/$bookingId")
            .header("X-Demo-Attendee", "alice"))
            .andExpect(status().isNoContent)

        // Replay the same key — returns original booking (now cancelled)
        val replayResult = mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-replay-cancel-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isOk)
            .andReturn()
        val replayed = mapper.readValue<Map<String, Any>>(replayResult.response.contentAsString)
        assertEquals(bookingId, replayed["id"])
    }

    @Test
    fun `notification created with booking`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-notif-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        val activityResult = mvc.perform(get("/api/demo/activity"))
            .andExpect(status().isOk)
            .andReturn()
        val activity = mapper.readValue<Map<String, Any>>(activityResult.response.contentAsString)
        @Suppress("UNCHECKED_CAST")
        val notifications = activity["notifications"] as List<Map<String, Any>>
        assertEquals(1, notifications.size)
        assertEquals("PENDING", notifications[0]["status"])
    }

    @Test
    fun `dispatch delivers notification`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-dispatch-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        val dispatchResult = mvc.perform(post("/api/demo/dispatch"))
            .andExpect(status().isOk)
            .andReturn()
        val dispatch = mapper.readValue<Map<String, Any>>(dispatchResult.response.contentAsString)
        @Suppress("UNCHECKED_CAST")
        val dispatched = dispatch["dispatched"] as List<Map<String, Any>>
        assertEquals(1, dispatched.size)
        assertEquals("DELIVERED", dispatched[0]["outcome"])
    }

    @Test
    fun `gateway deduplicates delivery`() {
        mvc.perform(post("/api/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Demo-Attendee", "alice")
            .header("Idempotency-Key", "key-dedup-1")
            .content("""{"sessionId":"$SESSION_1"}"""))
            .andExpect(status().isCreated)

        // Enable timeout so first dispatch doesn't mark delivered
        mvc.perform(post("/api/demo/simulate-timeout?enabled=true"))
        mvc.perform(post("/api/demo/dispatch"))

        // Disable timeout and dispatch again
        mvc.perform(post("/api/demo/simulate-timeout?enabled=false"))
        mvc.perform(post("/api/demo/dispatch"))

        // Check gateway has exactly one delivery
        val activityResult = mvc.perform(get("/api/demo/activity"))
            .andExpect(status().isOk)
            .andReturn()
        val activity = mapper.readValue<Map<String, Any>>(activityResult.response.contentAsString)
        @Suppress("UNCHECKED_CAST")
        val deliveries = activity["gatewayDeliveries"] as List<Map<String, Any>>
        assertEquals(1, deliveries.size, "Gateway should have exactly one delivery (deduplicated)")
    }
}
