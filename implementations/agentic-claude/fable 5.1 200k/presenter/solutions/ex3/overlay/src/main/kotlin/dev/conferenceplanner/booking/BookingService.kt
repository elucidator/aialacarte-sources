package dev.conferenceplanner.booking

import dev.conferenceplanner.api.ApiException
import dev.conferenceplanner.api.ErrorCode
import dev.conferenceplanner.demo.ActivityLog
import dev.conferenceplanner.notification.ConfirmationOutbox
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

data class BookingResult(val booking: Booking, val created: Boolean)

/**
 * Transaction boundaries live here: one transaction per public method. Everything inside either
 * commits together (booking + request key + pending confirmation) or not at all.
 */
@Service
class BookingService(
    private val repository: BookingRepository,
    private val outbox: ConfirmationOutbox,
    private val activity: ActivityLog,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun book(attendeeId: String, requestKey: String, sessionId: Long): BookingResult = transaction.execute {
        requireKnownAttendee(attendeeId)

        if (!repository.claimRequest(attendeeId, requestKey, sessionId)) {
            val completed = repository.findCompletedRequest(attendeeId, requestKey)!!
            if (completed.sessionId != sessionId) {
                throw ApiException(ErrorCode.REQUEST_KEY_REUSED, "Idempotency-Key was already used with a different payload")
            }
            // Replay: return the original booking as it is now, even if it has been cancelled since.
            return@execute BookingResult(repository.findBooking(completed.bookingId)!!, created = false)
        }

        val session = repository.lockSession(sessionId)
            ?: throw ApiException(ErrorCode.SESSION_NOT_FOUND, "Session $sessionId does not exist")
        if (repository.hasActiveBooking(sessionId, attendeeId)) {
            throw ApiException(ErrorCode.ALREADY_BOOKED, "Attendee already has an active booking for this session")
        }
        if (repository.countActive(sessionId) >= session.capacity) {
            throw ApiException(ErrorCode.SESSION_FULL, "Session '${session.title}' is full")
        }

        val bookingId = repository.insertBooking(sessionId, attendeeId)
        repository.completeRequest(attendeeId, requestKey, bookingId)
        // Invariant 5: the confirmation is queued in the same transaction; delivery happens later.
        outbox.enqueue(bookingId, deliveryKey = "confirmation-booking-$bookingId")
        activity.record("BOOKED", "$attendeeId booked '${session.title}' (booking $bookingId)")
        BookingResult(repository.findBooking(bookingId)!!, created = true)
    }!!

    fun cancel(attendeeId: String, bookingId: Long) {
        transaction.executeWithoutResult {
            val booking = repository.lockBookingOwnedBy(bookingId, attendeeId)
                ?: throw ApiException(ErrorCode.BOOKING_NOT_FOUND, "Booking $bookingId not found for this attendee")
            if (booking.status == BookingStatus.ACTIVE) {
                repository.cancel(bookingId)
                activity.record("CANCELLED", "$attendeeId cancelled '${booking.sessionTitle}' (booking $bookingId)")
            }
            // Already cancelled: nothing to release; still 204.
        }
    }

    fun activeBookings(attendeeId: String): List<Booking> = transaction.execute {
        requireKnownAttendee(attendeeId)
        repository.listActive(attendeeId)
    }!!

    private fun requireKnownAttendee(attendeeId: String) {
        if (!repository.attendeeExists(attendeeId)) {
            throw ApiException(ErrorCode.UNKNOWN_ATTENDEE, "Unknown demo attendee '$attendeeId'")
        }
    }
}
