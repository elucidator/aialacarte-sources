package com.conference.planner.api

data class ApiError(
    val code: String,
    val message: String,
)

data class SessionView(
    val id: Long,
    val title: String,
    val room: String,
    val capacity: Int,
    val activeBookings: Int,
    val availablePlaces: Int,
)

data class SessionPage(
    val items: List<SessionView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)

data class BookingRequestBody(
    val sessionId: Long?,
)

data class BookingView(
    val id: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val attendeeId: Long,
    val attendeeName: String,
    val status: String,
)

data class ActivityItem(
    val id: Long,
    val kind: String,
    val message: String,
    val createdAt: String,
)

data class DemoStatus(
    val simulateNotificationFailure: Boolean,
    val pendingConfirmations: Int,
    val acceptedDeliveries: Int,
    val acknowledgements: Int,
    val recentActivity: List<ActivityItem>,
)
