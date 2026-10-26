package com.conference.planner.booking

import com.conference.planner.api.ApiException
import com.conference.planner.api.BookingView
import com.conference.planner.api.SessionPage
import com.conference.planner.api.SessionView
import com.conference.planner.demo.DemoActivityService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Booking orchestration with explicit transaction boundaries (TransactionTemplate).
 *
 * createBooking (single TX):
 *  1. Look up prior idempotency record (attendee + key).
 *  2. Lock the session row FOR UPDATE (coordinates capacity across app processes).
 *  3. Reject when full or when the attendee already has an active booking.
 *  4. Insert ACTIVE booking + idempotency row + pending confirmation outbox together.
 *
 * cancelBooking (single TX):
 *  - Transition ACTIVE -> CANCELLED once; repeats return success without freeing another place.
 *
 * Replay after cancellation returns the original booking row (status CANCELLED) and does not rebook.
 */
@Service
class BookingService(
    private val repo: PostgresBookingRepository,
    private val demoActivity: DemoActivityService,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun listSessions(page: Int, size: Int): SessionPage {
        if (page < 0) {
            throw ApiException(400, "INVALID_PAGE", "page must be >= 0")
        }
        if (size < 1 || size > 100) {
            throw ApiException(400, "INVALID_SIZE", "size must be between 1 and 100")
        }
        val total = repo.countSessions()
        val items = repo.listSessions(size, page * size).map { it.toView() }
        return SessionPage(items = items, page = page, size = size, totalElements = total)
    }

    fun createBooking(attendeeId: Long, sessionId: Long, idempotencyKey: String): Pair<BookingView, Boolean> {
        if (idempotencyKey.isBlank()) {
            throw ApiException(400, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required")
        }
        if (!repo.attendeeExists(attendeeId)) {
            throw ApiException(400, "UNKNOWN_ATTENDEE", "Unknown demo attendee id: $attendeeId")
        }

        return tx.execute {
            createBookingInTransaction(attendeeId, sessionId, idempotencyKey)
        }!!
    }

    private fun createBookingInTransaction(
        attendeeId: Long,
        sessionId: Long,
        idempotencyKey: String,
    ): Pair<BookingView, Boolean> {
        val existing = repo.findBookingRequest(attendeeId, idempotencyKey)
        if (existing != null) {
            if (existing.sessionId != sessionId) {
                throw ApiException(
                    409,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different sessionId",
                )
            }
            val booking = repo.findBooking(existing.bookingId)
                ?: throw ApiException(500, "INTERNAL", "Booking missing for stored request")
            return booking.toView() to false
        }

        val session = repo.findSessionForUpdate(sessionId)
            ?: throw ApiException(404, "SESSION_NOT_FOUND", "Unknown session id: $sessionId")

        if (session.activeBookings >= session.capacity) {
            throw ApiException(409, "SESSION_FULL", "Session is at full capacity")
        }
        if (repo.hasActiveBooking(sessionId, attendeeId)) {
            throw ApiException(
                409,
                "DUPLICATE_ACTIVE_BOOKING",
                "Attendee already has an active booking for this session",
            )
        }

        return try {
            val bookingId = repo.insertBooking(sessionId, attendeeId)
            repo.insertBookingRequest(attendeeId, idempotencyKey, sessionId, bookingId)
            val deliveryKey = "booking-$bookingId"
            repo.insertConfirmationOutbox(bookingId, deliveryKey)
            demoActivity.record(
                "BOOKING_ACCEPTED",
                "Booking $bookingId accepted for attendee $attendeeId on session $sessionId; confirmation pending ($deliveryKey)",
            )
            val booking = repo.findBooking(bookingId)!!
            booking.toView() to true
        } catch (ex: DataIntegrityViolationException) {
            val raced = repo.findBookingRequest(attendeeId, idempotencyKey)
            if (raced != null && raced.sessionId == sessionId) {
                val booking = repo.findBooking(raced.bookingId)!!
                return booking.toView() to false
            }
            if (repo.hasActiveBooking(sessionId, attendeeId)) {
                throw ApiException(
                    409,
                    "DUPLICATE_ACTIVE_BOOKING",
                    "Attendee already has an active booking for this session",
                )
            }
            throw ApiException(409, "CONFLICT", "Booking conflict: ${ex.mostSpecificCause.message}")
        }
    }

    fun listBookings(attendeeId: Long): List<BookingView> {
        if (!repo.attendeeExists(attendeeId)) {
            throw ApiException(400, "UNKNOWN_ATTENDEE", "Unknown demo attendee id: $attendeeId")
        }
        return repo.listActiveBookings(attendeeId).map { it.toView() }
    }

    fun cancelBooking(attendeeId: Long, bookingId: Long) {
        if (!repo.attendeeExists(attendeeId)) {
            throw ApiException(400, "UNKNOWN_ATTENDEE", "Unknown demo attendee id: $attendeeId")
        }
        tx.executeWithoutResult {
            if (!repo.bookingOwnedBy(bookingId, attendeeId)) {
                throw ApiException(404, "BOOKING_NOT_FOUND", "Booking not found for this attendee")
            }
            val updated = repo.cancelActiveBooking(bookingId, attendeeId)
            if (updated > 0) {
                demoActivity.record(
                    "BOOKING_CANCELLED",
                    "Booking $bookingId cancelled by attendee $attendeeId",
                )
            }
        }
    }

    private fun SessionRow.toView() = SessionView(
        id = id,
        title = title,
        room = room,
        capacity = capacity,
        activeBookings = activeBookings,
        availablePlaces = (capacity - activeBookings).coerceAtLeast(0),
    )

    private fun BookingRow.toView() = BookingView(
        id = id,
        sessionId = sessionId,
        sessionTitle = sessionTitle,
        attendeeId = attendeeId,
        attendeeName = attendeeName,
        status = status,
    )
}
