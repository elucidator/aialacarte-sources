package com.example.conferenceplanner.service

import com.example.conferenceplanner.gateway.DeliveryOutcome
import com.example.conferenceplanner.gateway.FakeGateway
import com.example.conferenceplanner.gateway.GatewayTimeoutException
import com.example.conferenceplanner.model.DispatchSummary
import com.example.conferenceplanner.repository.BookingRepository
import com.example.conferenceplanner.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Bounded dispatcher: a single manual dispatch call attempts each pending confirmation
 * at most once. There is no background loop and no automatic backoff; a failed attempt
 * stays pending until the next dispatch. The delivery key stored with the booking is
 * reused on every attempt, including across restarts.
 */
@Service
class NotificationDispatcher(
    private val notifications: NotificationRepository,
    private val bookings: BookingRepository,
    private val gateway: FakeGateway,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun dispatchPending(limit: Int = 50): DispatchSummary {
        val pending = notifications.listPending(limit)
        var accepted = 0
        var duplicates = 0
        var timedOut = 0
        var failed = 0
        var acknowledged = 0

        for (notification in pending) {
            try {
                val outcome = gateway.deliver(notification.deliveryKey, notification.bookingId)
                if (outcome == DeliveryOutcome.ACCEPTED) accepted++ else duplicates++
                tx.execute {
                    val attempt = notifications.incrementAttempts(notification.deliveryKey)
                    notifications.insertAttempt(
                        notification.deliveryKey,
                        attempt,
                        outcome.name,
                        "gateway accepted ${if (outcome == DeliveryOutcome.ACCEPTED) "a new message" else "a duplicate of an existing message"}",
                    )
                    notifications.markAcknowledged(notification.deliveryKey)
                }
                acknowledged++
                bookings.addActivity(
                    "DELIVERY_ACKNOWLEDGED",
                    "${notification.deliveryKey}: attempt $outcome, confirmation acknowledged",
                )
            } catch (e: GatewayTimeoutException) {
                timedOut++
                recordFailedAttempt(notification.deliveryKey, notification.bookingId, "TIMEOUT", e.message ?: "timeout")
            } catch (e: Exception) {
                failed++
                recordFailedAttempt(notification.deliveryKey, notification.bookingId, "ERROR", e.message ?: "delivery error")
            }
        }

        return DispatchSummary(
            attempted = pending.size,
            accepted = accepted,
            duplicates = duplicates,
            timedOut = timedOut,
            failed = failed,
            acknowledged = acknowledged,
            stillPending = notifications.countPending().toInt(),
        )
    }

    private fun recordFailedAttempt(deliveryKey: String, bookingId: Long, outcome: String, detail: String) {
        tx.execute {
            val attempt = notifications.incrementAttempts(deliveryKey)
            notifications.insertAttempt(deliveryKey, attempt, outcome, detail)
        }
        bookings.addActivity(
            "DELIVERY_$outcome",
            "$deliveryKey (booking $bookingId): $detail; notification stays PENDING",
        )
    }
}
