package com.example.conferenceplanner.web

import com.example.conferenceplanner.model.BookingView
import com.example.conferenceplanner.model.CreateBookingRequest
import com.example.conferenceplanner.service.BookingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bookings")
class BookingController(private val bookingService: BookingService) {

    @PostMapping
    fun create(
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendee: String?,
        @RequestHeader(value = "Idempotency-Key", required = false) requestKey: String?,
        @RequestBody request: CreateBookingRequest,
    ): ResponseEntity<BookingView> {
        val who = requireAttendee(attendee)
        val key = requireRequestKey(requestKey)
        val sessionId = request.sessionId
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Field 'sessionId' is required")
        if (sessionId < 1) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SESSION_ID",
                "sessionId must be a positive number but was $sessionId",
            )
        }
        val result = bookingService.book(who, key, sessionId)
        val status = if (result.replayed) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(result.booking)
    }

    @GetMapping
    fun list(
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendee: String?,
    ): List<BookingView> = bookingService.myBookings(requireAttendee(attendee))

    @DeleteMapping("/{id}")
    fun cancel(
        @RequestHeader(value = "X-Demo-Attendee", required = false) attendee: String?,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        bookingService.cancel(requireAttendee(attendee), id)
        return ResponseEntity.noContent().build()
    }

    private fun requireAttendee(attendee: String?): String =
        attendee?.takeIf { it.isNotBlank() }
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "MISSING_ATTENDEE", "Header 'X-Demo-Attendee' is required")

    private fun requireRequestKey(requestKey: String?): String =
        requestKey?.takeIf { it.isNotBlank() }
            ?: throw ApiException(
                HttpStatus.BAD_REQUEST,
                "MISSING_IDEMPOTENCY_KEY",
                "Header 'Idempotency-Key' is required",
            )
}
