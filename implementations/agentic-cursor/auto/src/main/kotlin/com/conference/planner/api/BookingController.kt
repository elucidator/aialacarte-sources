package com.conference.planner.api

import com.conference.planner.booking.BookingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class BookingController(
    private val bookingService: BookingService,
) {
    @GetMapping("/sessions")
    fun sessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): SessionPage = bookingService.listSessions(page, size)

    @PostMapping("/bookings")
    fun create(
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendeeHeader: String?,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody body: BookingRequestBody,
    ): ResponseEntity<BookingView> {
        val attendeeId = parseAttendee(attendeeHeader)
        val sessionId = body.sessionId
            ?: throw ApiException(400, "MISSING_SESSION_ID", "sessionId is required")
        val key = idempotencyKey
            ?: throw ApiException(400, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required")

        val (booking, created) = bookingService.createBooking(attendeeId, sessionId, key)
        return if (created) {
            ResponseEntity.status(HttpStatus.CREATED).body(booking)
        } else {
            ResponseEntity.ok(booking)
        }
    }

    @GetMapping("/bookings")
    fun list(
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendeeHeader: String?,
    ): List<BookingView> {
        val attendeeId = parseAttendee(attendeeHeader)
        return bookingService.listBookings(attendeeId)
    }

    @DeleteMapping("/bookings/{id}")
    fun cancel(
        @PathVariable id: Long,
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendeeHeader: String?,
    ): ResponseEntity<Void> {
        val attendeeId = parseAttendee(attendeeHeader)
        bookingService.cancelBooking(attendeeId, id)
        return ResponseEntity.noContent().build()
    }

    private fun parseAttendee(header: String?): Long {
        if (header.isNullOrBlank()) {
            throw ApiException(400, "MISSING_ATTENDEE", "X-Demo-Attendee header is required")
        }
        return header.toLongOrNull()
            ?: throw ApiException(400, "INVALID_ATTENDEE", "X-Demo-Attendee must be a numeric id")
    }
}
