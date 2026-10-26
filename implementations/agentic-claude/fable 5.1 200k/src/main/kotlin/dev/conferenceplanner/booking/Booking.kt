package dev.conferenceplanner.booking

import java.time.Instant

enum class BookingStatus { ACTIVE, CANCELLED }

data class Booking(
    val id: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val attendeeId: String,
    val status: BookingStatus,
    val createdAt: Instant,
    val cancelledAt: Instant?,
)

/** A previously completed request for the same attendee and request key. */
data class CompletedRequest(val sessionId: Long, val bookingId: Long)

data class LockedSession(val id: Long, val title: String, val capacity: Int)
