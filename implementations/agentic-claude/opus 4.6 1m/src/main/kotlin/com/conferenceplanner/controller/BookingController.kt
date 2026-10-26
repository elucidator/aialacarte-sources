package com.conferenceplanner.controller

import com.conferenceplanner.model.ApiError
import com.conferenceplanner.model.BookingRequest
import com.conferenceplanner.service.BookingResult
import com.conferenceplanner.service.BookingService
import com.conferenceplanner.service.CancelResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class BookingController(private val bookingService: BookingService) {

    @PostMapping("/api/bookings")
    fun createBooking(
        @RequestBody request: BookingRequest,
        @RequestHeader("X-Demo-Attendee") attendee: String?,
        @RequestHeader("Idempotency-Key") idempotencyKey: String?
    ): ResponseEntity<Any> {
        if (attendee.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiError("MISSING_ATTENDEE", "X-Demo-Attendee header is required")
            )
        }
        if (idempotencyKey.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiError("MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required")
            )
        }
        if (request.sessionId == null) {
            return ResponseEntity.badRequest().body(
                ApiError("MISSING_SESSION_ID", "sessionId is required")
            )
        }

        return when (val result = bookingService.createBooking(request.sessionId, attendee, idempotencyKey)) {
            is BookingResult.Success -> {
                val status = if (result.replay) HttpStatus.OK else HttpStatus.CREATED
                ResponseEntity.status(status).body(result.booking)
            }
            is BookingResult.SessionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError("SESSION_NOT_FOUND", "Session not found")
            )
            is BookingResult.SessionFull -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError("SESSION_FULL", "Session is full")
            )
            is BookingResult.AlreadyBooked -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError("ALREADY_BOOKED", "You already have an active booking for this session")
            )
            is BookingResult.KeyConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError("KEY_CONFLICT", "Idempotency key was used with different parameters")
            )
        }
    }

    @GetMapping("/api/bookings")
    fun listBookings(
        @RequestHeader("X-Demo-Attendee") attendee: String?
    ): ResponseEntity<Any> {
        if (attendee.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiError("MISSING_ATTENDEE", "X-Demo-Attendee header is required")
            )
        }
        return ResponseEntity.ok(bookingService.listBookings(attendee))
    }

    @DeleteMapping("/api/bookings/{id}")
    fun cancelBooking(
        @PathVariable id: UUID,
        @RequestHeader("X-Demo-Attendee") attendee: String?
    ): ResponseEntity<Any> {
        if (attendee.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiError("MISSING_ATTENDEE", "X-Demo-Attendee header is required")
            )
        }

        return when (bookingService.cancelBooking(id, attendee)) {
            is CancelResult.Cancelled -> ResponseEntity.noContent().build()
            is CancelResult.AlreadyCancelled -> ResponseEntity.noContent().build()
            is CancelResult.NotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError("BOOKING_NOT_FOUND", "Booking not found")
            )
        }
    }
}
