package dev.conferenceplanner.booking

/**
 * Booking persistence as seen by BookingService. Implementations run inside the caller's
 * transaction and never commit on their own; lockSession and lockBookingOwnedBy must hold a
 * row lock until that transaction ends.
 */
interface BookingRepository {
    fun attendeeExists(attendeeId: String): Boolean
    fun claimRequest(attendeeId: String, requestKey: String, sessionId: Long): Boolean
    fun findCompletedRequest(attendeeId: String, requestKey: String): CompletedRequest?
    fun completeRequest(attendeeId: String, requestKey: String, bookingId: Long)
    fun lockSession(sessionId: Long): LockedSession?
    fun countActive(sessionId: Long): Int
    fun hasActiveBooking(sessionId: Long, attendeeId: String): Boolean
    fun insertBooking(sessionId: Long, attendeeId: String): Long
    fun findBooking(bookingId: Long): Booking?
    fun lockBookingOwnedBy(bookingId: Long, attendeeId: String): Booking?
    fun cancel(bookingId: Long)
    fun listActive(attendeeId: String): List<Booking>
}
