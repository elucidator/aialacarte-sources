package com.example.conferenceplanner.model

import java.time.Instant

data class SessionSummary(
    val id: Long,
    val title: String,
    val room: String,
    val capacity: Int,
    val activeBookings: Int,
    val availablePlaces: Int,
)

data class BookingView(
    val id: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val sessionRoom: String,
    val attendee: String,
    val status: String,
    val createdAt: Instant,
    val cancelledAt: Instant?,
)

data class NotificationView(
    val deliveryKey: String,
    val bookingId: Long,
    val sessionTitle: String,
    val attendee: String,
    val status: String,
    val attempts: Int,
    val acceptedDeliveries: Int,
    val createdAt: Instant,
    val acknowledgedAt: Instant?,
)

data class ActivityEntry(
    val id: Long,
    val kind: String,
    val message: String,
    val createdAt: Instant,
)

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)

data class CreateBookingRequest(val sessionId: Long?)

data class ToggleRequest(val enabled: Boolean)

data class DispatchSummary(
    val attempted: Int,
    val accepted: Int,
    val duplicates: Int,
    val timedOut: Int,
    val failed: Int,
    val acknowledged: Int,
    val stillPending: Int,
)

data class DemoState(
    val controls: Map<String, String>,
    val pendingNotifications: Long,
    val acknowledgedNotifications: Long,
    val totalBookings: Long,
    val totalActivity: Long,
)
