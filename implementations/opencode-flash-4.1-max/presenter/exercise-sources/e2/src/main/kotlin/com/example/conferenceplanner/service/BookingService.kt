package com.example.conferenceplanner.service

import com.example.conferenceplanner.model.BookingView
import com.example.conferenceplanner.repository.BookingRepository
import com.example.conferenceplanner.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest

@Service
class BookingService(
    private val repository: BookingRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    data class BookResult(val booking: BookingView, val replayed: Boolean)

    /**
     * One transaction: idempotency claim -> session lookup -> duplicate check ->
     * capacity check -> booking + pending notification -> attach claim.
     */
    fun book(attendee: String, requestKey: String, sessionId: Long): BookResult =
        tx.execute { _ ->
            val payloadHash = payloadHash(sessionId)
            val claimed = repository.insertIdempotencyKey(attendee, requestKey, payloadHash)
            if (!claimed) {
                val existing = repository.findIdempotencyKey(attendee, requestKey)
                    ?: error("idempotency claim vanished for key '$requestKey'")
                if (existing.payloadHash != payloadHash) {
                    throw ApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "Request key '$requestKey' was already used with a different payload",
                    )
                }
                val booking = repository.findBooking(existing.bookingId!!)
                    ?: error("booking ${existing.bookingId} for request key '$requestKey' not found")
                return@execute BookResult(booking, replayed = true)
            }

            val session = repository.findSession(sessionId)
                ?: throw ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "No session with id $sessionId")

            if (repository.findActiveBookingId(sessionId, attendee) != null) {
                throw ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_BOOKING",
                    "$attendee already has an active booking for session $sessionId",
                )
            }

            val active = repository.countActiveBookings(sessionId)
            if (active >= session.capacity) {
                throw ApiException(
                    HttpStatus.CONFLICT,
                    "SESSION_FULL",
                    "Session ${session.id} '${session.title}' is full (${session.capacity}/${session.capacity})",
                )
            }

            val bookingId = repository.insertBooking(sessionId, attendee)
            repository.insertNotification(bookingId, "booking:$bookingId")
            repository.attachIdempotencyBooking(attendee, requestKey, bookingId)
            repository.addActivity(
                "BOOKING_CREATED",
                "Booking $bookingId: $attendee took a place in '${session.title}'",
            )
            val booking = repository.findBooking(bookingId) ?: error("inserted booking $bookingId not found")
            BookResult(booking, replayed = false)
        } ?: error("booking transaction returned no result")

    /** Returns true when this call cancelled the booking; false when it was already cancelled. */
    fun cancel(attendee: String, bookingId: Long): Boolean =
        tx.execute { _ ->
            val booking = repository.findBookingForUpdate(bookingId)
                ?: throw ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No booking with id $bookingId")
            if (booking.attendee != attendee) {
                throw ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No booking with id $bookingId")
            }
            if (booking.status == "CANCELLED") {
                return@execute false
            }
            repository.cancelBooking(bookingId)
            repository.addActivity(
                "BOOKING_CANCELLED",
                "Booking $bookingId: $attendee cancelled '${booking.sessionTitle}'",
            )
            true
        } ?: error("cancellation transaction returned no result")

    fun myBookings(attendee: String): List<BookingView> = repository.listActiveBookings(attendee)

    private fun payloadHash(sessionId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("sessionId=$sessionId".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
