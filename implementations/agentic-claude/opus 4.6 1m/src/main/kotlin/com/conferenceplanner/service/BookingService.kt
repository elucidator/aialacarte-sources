package com.conferenceplanner.service

import com.conferenceplanner.model.*
import com.conferenceplanner.repository.BookingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class BookingService(
    private val repository: BookingRepository,
    private val transactionTemplate: TransactionTemplate
) {

    fun listSessions(page: Int, size: Int): PagedResult<SessionView> {
        return repository.findSessionsWithCounts(page, size)
    }

    /**
     * Creates a booking within a SERIALIZABLE transaction to prevent overbooking.
     *
     * The transaction uses SELECT ... FOR UPDATE on the session row to serialize
     * concurrent booking attempts for the same session. This ensures:
     * 1. Active bookings never exceed session capacity
     * 2. Idempotency key replay returns original booking
     * 3. Duplicate attendee/session booking is rejected
     */
    fun createBooking(sessionId: UUID, attendee: String, idempotencyKey: String): BookingResult {
        return transactionTemplate.execute { _ ->
            // Check idempotency key first (outside session lock)
            val existing = repository.findByIdempotencyKey(idempotencyKey)
            if (existing != null) {
                if (existing.attendee != attendee || existing.sessionId != sessionId) {
                    return@execute BookingResult.KeyConflict
                }
                return@execute BookingResult.Success(existing, replay = true)
            }

            // Lock the session row to serialize concurrent bookings
            val session = repository.findSessionForUpdate(sessionId)
                ?: return@execute BookingResult.SessionNotFound

            val activeForSession = repository.findActiveByAttendeeAndSession(attendee, sessionId)
            if (activeForSession != null) {
                return@execute BookingResult.AlreadyBooked
            }

            val activeCount = repository.countActiveBookings(sessionId)
            if (activeCount >= session.capacity) {
                return@execute BookingResult.SessionFull
            }

            val bookingId = UUID.randomUUID()
            val deliveryKey = "confirm-$bookingId"
            val booking = repository.insertBookingAndNotification(
                bookingId, sessionId, attendee, idempotencyKey, deliveryKey
            )
            BookingResult.Success(booking, replay = false)
        }!!
    }

    fun listBookings(attendee: String): List<Booking> {
        return repository.findActiveByAttendee(attendee)
    }

    fun cancelBooking(bookingId: UUID, attendee: String): CancelResult {
        return transactionTemplate.execute { _ ->
            val booking = repository.findById(bookingId)
                ?: return@execute CancelResult.NotFound

            if (booking.attendee != attendee) {
                return@execute CancelResult.NotFound
            }

            if (booking.status == BookingStatus.CANCELLED) {
                return@execute CancelResult.AlreadyCancelled
            }

            repository.cancelBooking(bookingId)
            CancelResult.Cancelled
        }!!
    }
}

sealed class BookingResult {
    data class Success(val booking: Booking, val replay: Boolean) : BookingResult()
    data object SessionNotFound : BookingResult()
    data object SessionFull : BookingResult()
    data object AlreadyBooked : BookingResult()
    data object KeyConflict : BookingResult()
}

sealed class CancelResult {
    data object Cancelled : CancelResult()
    data object AlreadyCancelled : CancelResult()
    data object NotFound : CancelResult()
}
