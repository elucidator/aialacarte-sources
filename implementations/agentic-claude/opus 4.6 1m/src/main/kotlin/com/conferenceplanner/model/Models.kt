package com.conferenceplanner.model

import java.time.Instant
import java.util.UUID

data class Session(
    val id: UUID,
    val title: String,
    val room: String,
    val capacity: Int,
    val sortOrder: Int
)

data class SessionView(
    val id: UUID,
    val title: String,
    val room: String,
    val capacity: Int,
    val activeBookings: Int,
    val availablePlaces: Int
)

data class Booking(
    val id: UUID,
    val sessionId: UUID,
    val attendee: String,
    val idempotencyKey: String,
    val status: BookingStatus,
    val createdAt: Instant
)

enum class BookingStatus { ACTIVE, CANCELLED }

data class Notification(
    val id: UUID,
    val bookingId: UUID,
    val deliveryKey: String,
    val status: NotificationStatus,
    val attempts: Int,
    val lastAttemptAt: Instant?,
    val createdAt: Instant
)

enum class NotificationStatus { PENDING, DELIVERED }

data class GatewayDelivery(
    val id: UUID,
    val deliveryKey: String,
    val payload: String,
    val accepted: Boolean,
    val acknowledged: Boolean,
    val createdAt: Instant
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
)

data class ApiError(
    val code: String,
    val message: String
)

data class BookingRequest(
    val sessionId: UUID?
)
