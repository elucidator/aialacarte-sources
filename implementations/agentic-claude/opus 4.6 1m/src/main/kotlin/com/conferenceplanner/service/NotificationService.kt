package com.conferenceplanner.service

import com.conferenceplanner.model.GatewayDelivery
import com.conferenceplanner.model.Notification
import com.conferenceplanner.repository.BookingRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Service
class NotificationService(
    private val repository: BookingRepository,
    private val transactionTemplate: TransactionTemplate,
    private val gatewayJdbc: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val simulateTimeout = AtomicBoolean(false)

    fun setSimulateTimeout(enabled: Boolean) {
        simulateTimeout.set(enabled)
    }

    fun isSimulatingTimeout(): Boolean = simulateTimeout.get()

    fun dispatchPending(): List<DispatchResult> {
        val pending = repository.findPendingNotifications()
        return pending.map { dispatch(it) }
    }

    private fun dispatch(notification: Notification): DispatchResult {
        transactionTemplate.execute { _ ->
            repository.incrementNotificationAttempt(notification.id)
        }

        val sessionTitle = repository.findSessionTitle(
            repository.findById(notification.bookingId)!!.sessionId
        ) ?: "Unknown"
        val payload = "Booking confirmed for $sessionTitle"

        try {
            val delivery = deliverToGateway(notification.deliveryKey, payload)

            if (simulateTimeout.get()) {
                log.info("Simulating timeout after gateway accepted delivery_key={}", notification.deliveryKey)
                return DispatchResult(notification.id, notification.deliveryKey, "TIMEOUT_AFTER_ACCEPT",
                    gatewayAccepted = true, acknowledged = false)
            }

            transactionTemplate.execute { _ ->
                repository.markNotificationDelivered(notification.id)
            }
            repository.acknowledgeGatewayDelivery(delivery.id)

            return DispatchResult(notification.id, notification.deliveryKey, "DELIVERED",
                gatewayAccepted = delivery.accepted, acknowledged = true)

        } catch (e: Exception) {
            log.error("Dispatch failed for notification={}: {}", notification.id, e.message)
            return DispatchResult(notification.id, notification.deliveryKey, "FAILED",
                gatewayAccepted = false, acknowledged = false)
        }
    }

    private fun deliverToGateway(deliveryKey: String, payload: String): GatewayDelivery {
        val existing = repository.findGatewayDeliveryByKey(deliveryKey)
        if (existing != null) {
            return existing
        }
        return repository.insertGatewayDelivery(UUID.randomUUID(), deliveryKey, payload)
    }

    fun getActivity(): Map<String, Any> {
        val notifications = repository.findAllNotificationsForActivity()
        val deliveries = repository.findAllGatewayDeliveries()
        return mapOf(
            "notifications" to notifications,
            "gatewayDeliveries" to deliveries.map { d ->
                mapOf(
                    "id" to d.id,
                    "deliveryKey" to d.deliveryKey,
                    "payload" to d.payload,
                    "accepted" to d.accepted,
                    "acknowledged" to d.acknowledged,
                    "createdAt" to d.createdAt
                )
            }
        )
    }
}

data class DispatchResult(
    val notificationId: UUID,
    val deliveryKey: String,
    val outcome: String,
    val gatewayAccepted: Boolean,
    val acknowledged: Boolean
)
