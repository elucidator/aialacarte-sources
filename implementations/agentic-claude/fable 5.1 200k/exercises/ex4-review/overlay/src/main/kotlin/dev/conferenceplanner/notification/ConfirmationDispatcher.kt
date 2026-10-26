package dev.conferenceplanner.notification

import dev.conferenceplanner.demo.ActivityLog
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

data class DispatchReport(val processed: Int, val acknowledged: Int, val duplicates: Int, val timeouts: Int)

/**
 * Bounded, manually triggered dispatcher. Each attempt is sent under its own delivery key
 * (the booking's key plus the attempt number) so operators can see which attempt reached the
 * gateway and retries are never mistaken for the previous attempt.
 *
 * No transaction is held while the gateway is called; the outcome is recorded afterwards in a
 * short transaction. If the process dies in between, the confirmation simply stays PENDING.
 */
@Service
class ConfirmationDispatcher(
    private val outbox: ConfirmationOutbox,
    private val gateway: FakeGateway,
    private val activity: ActivityLog,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun dispatchPending(): DispatchReport {
        val batch = transaction.execute { outbox.pending(limit = BATCH_SIZE, maxAttempts = MAX_ATTEMPTS) }!!
        var acknowledged = 0
        var duplicates = 0
        var timeouts = 0
        for (confirmation in batch) {
            val attemptNo = confirmation.attempts + 1
            val deliveryKey = "${confirmation.deliveryKey}-attempt-$attemptNo"
            val outcome = try {
                val ack = gateway.deliver(deliveryKey, confirmation.attendeeId, "Your place for '${confirmation.sessionTitle}' is confirmed")
                if (ack.duplicate) AttemptOutcome.DUPLICATE_ACKNOWLEDGED else AttemptOutcome.ACKNOWLEDGED
            } catch (e: GatewayTimeoutException) {
                AttemptOutcome.TIMEOUT
            }
            when (outcome) {
                AttemptOutcome.ACKNOWLEDGED -> acknowledged++
                AttemptOutcome.DUPLICATE_ACKNOWLEDGED -> duplicates++
                AttemptOutcome.TIMEOUT -> timeouts++
            }
            transaction.executeWithoutResult {
                outbox.recordAttempt(confirmation.bookingId, attemptNo, deliveryKey, outcome)
                if (outcome != AttemptOutcome.TIMEOUT) outbox.markDelivered(confirmation.bookingId)
                activity.record("DISPATCH", "booking ${confirmation.bookingId} attempt $attemptNo key $deliveryKey: $outcome")
            }
        }
        return DispatchReport(batch.size, acknowledged, duplicates, timeouts)
    }

    private companion object {
        const val BATCH_SIZE = 20
        const val MAX_ATTEMPTS = 5
    }
}
