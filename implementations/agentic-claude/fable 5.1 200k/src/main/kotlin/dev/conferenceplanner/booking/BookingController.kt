package dev.conferenceplanner.booking

import dev.conferenceplanner.api.ApiException
import dev.conferenceplanner.api.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

const val ATTENDEE_HEADER = "X-Demo-Attendee"
const val REQUEST_KEY_HEADER = "Idempotency-Key"

data class BookingRequestBody(val sessionId: Long?)

@RestController
class BookingController(private val bookings: BookingService) {

    @PostMapping("/api/bookings")
    fun book(
        @RequestHeader(ATTENDEE_HEADER, required = false) attendee: String?,
        @RequestHeader(REQUEST_KEY_HEADER, required = false) requestKey: String?,
        @RequestBody(required = false) body: BookingRequestBody?,
    ): ResponseEntity<Booking> {
        val sessionId = body?.sessionId ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "sessionId is required")
        val result = bookings.book(requireAttendee(attendee), requireRequestKey(requestKey), sessionId)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.booking)
    }

    @GetMapping("/api/bookings")
    fun list(@RequestHeader(ATTENDEE_HEADER, required = false) attendee: String?): List<Booking> =
        bookings.activeBookings(requireAttendee(attendee))

    @DeleteMapping("/api/bookings/{id}")
    fun cancel(
        @RequestHeader(ATTENDEE_HEADER, required = false) attendee: String?,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        bookings.cancel(requireAttendee(attendee), id)
        return ResponseEntity.noContent().build()
    }

    private fun requireAttendee(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException(ErrorCode.MISSING_ATTENDEE, "$ATTENDEE_HEADER header is required")

    private fun requireRequestKey(value: String?): String {
        val key = value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException(ErrorCode.MISSING_REQUEST_KEY, "$REQUEST_KEY_HEADER header is required")
        if (key.length > 200) throw ApiException(ErrorCode.VALIDATION_ERROR, "$REQUEST_KEY_HEADER must be at most 200 characters")
        return key
    }
}
