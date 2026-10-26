package com.conference.planner.booking

data class SessionRow(
    val id: Long,
    val title: String,
    val room: String,
    val capacity: Int,
    val activeBookings: Int,
)

data class BookingRow(
    val id: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val attendeeId: Long,
    val attendeeName: String,
    val status: String,
)

data class BookingRequestRow(
    val attendeeId: Long,
    val idempotencyKey: String,
    val sessionId: Long,
    val bookingId: Long,
)
