package com.example.conferenceplanner.repository

import com.example.conferenceplanner.model.BookingView

/** Session row needed by the booking flow. */
data class SessionRow(val id: Long, val title: String, val room: String, val capacity: Int)

/** Attendee-scoped request key state. */
data class IdempotencyRow(val bookingId: Long?, val payloadHash: String)

/**
 * Persistence contract used by BookingService. Implementations must run inside the
 * caller's transaction: every decision is made on the Spring-bound connection so that
 * the session row lock taken by [lockSession] still serialises booking attempts.
 */
interface BookingRepository {

    fun lockSession(sessionId: Long): SessionRow?

    fun countActiveBookings(sessionId: Long): Int

    fun findActiveBookingId(sessionId: Long, attendee: String): Long?

    fun insertBooking(sessionId: Long, attendee: String): Long

    fun insertNotification(bookingId: Long, deliveryKey: String)

    fun insertIdempotencyKey(attendee: String, requestKey: String, payloadHash: String): Boolean

    fun findIdempotencyKey(attendee: String, requestKey: String): IdempotencyRow?

    fun attachIdempotencyBooking(attendee: String, requestKey: String, bookingId: Long)

    fun findBooking(bookingId: Long): BookingView?

    fun findBookingForUpdate(bookingId: Long): BookingView?

    fun cancelBooking(bookingId: Long)

    fun listActiveBookings(attendee: String): List<BookingView>

    fun addActivity(kind: String, message: String)
}
