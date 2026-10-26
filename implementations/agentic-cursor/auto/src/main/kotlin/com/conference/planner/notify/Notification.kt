package com.conference.planner.notify

import com.conference.planner.demo.DemoActivityService
import com.conference.planner.demo.DemoSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Fake notification gateway with its own transaction ownership.
 *
 * Acceptance is committed in a separate transaction before any acknowledgement
 * delay. A caller timeout cannot roll back a recorded acceptance. Deduplication
 * is by stable delivery_key (PRIMARY KEY).
 */
@Component
class FakeNotificationGateway(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    private val demoActivity: DemoActivityService,
    @Value("\${conference.notification.simulate-ack-timeout-ms:50}")
    private val ackTimeoutMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val gatewayTx = TransactionTemplate(transactionManager)

    data class SendResult(
        val accepted: Boolean,
        val duplicate: Boolean,
        val acknowledged: Boolean,
        val timedOut: Boolean,
    )

    /**
     * @param failAfterAccept when true, record acceptance then simulate timeout (no ack returned)
     */
    fun send(deliveryKey: String, bookingId: Long, payload: String, failAfterAccept: Boolean): SendResult {
        var duplicate = false
        var newlyAccepted = false

        gatewayTx.execute {
            val inserted = jdbc.update(
                """
                INSERT INTO gateway_messages (delivery_key, booking_id, payload)
                VALUES (?, ?, ?)
                ON CONFLICT (delivery_key) DO NOTHING
                """.trimIndent(),
                deliveryKey,
                bookingId,
                payload,
            )
            if (inserted == 1) {
                newlyAccepted = true
                demoActivity.record(
                    "GATEWAY_ACCEPTED",
                    "Gateway accepted delivery_key=$deliveryKey for booking $bookingId",
                )
            } else {
                duplicate = true
                demoActivity.record(
                    "GATEWAY_DEDUP",
                    "Gateway deduplicated delivery_key=$deliveryKey for booking $bookingId",
                )
            }
        }

        if (failAfterAccept) {
            // Acceptance already committed above; timeout is visible to the sender only.
            log.info("Simulating ack timeout after accept for {}", deliveryKey)
            try {
                Thread.sleep(ackTimeoutMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return SendResult(
                accepted = true,
                duplicate = duplicate,
                acknowledged = false,
                timedOut = true,
            )
        }

        gatewayTx.execute {
            jdbc.update(
                """
                INSERT INTO gateway_acknowledgements (delivery_key)
                VALUES (?)
                ON CONFLICT (delivery_key) DO NOTHING
                """.trimIndent(),
                deliveryKey,
            )
            demoActivity.record(
                "GATEWAY_ACK",
                "Gateway acknowledged delivery_key=$deliveryKey",
            )
        }

        return SendResult(
            accepted = newlyAccepted || duplicate,
            duplicate = duplicate,
            acknowledged = true,
            timedOut = false,
        )
    }
}

@Service
class ConfirmationDispatcher(
    private val jdbc: JdbcTemplate,
    private val gateway: FakeNotificationGateway,
    private val demoSettings: DemoSettingsRepository,
    private val demoActivity: DemoActivityService,
    transactionManager: PlatformTransactionManager,
    @Value("\${conference.notification.max-attempts:5}")
    private val maxAttempts: Int,
) {
    private val appTx = TransactionTemplate(transactionManager)

    data class DispatchSummary(
        val attempted: Int,
        val acknowledged: Int,
        val failed: Int,
        val skipped: Int,
    )

    fun dispatchPending(limit: Int = 20): DispatchSummary {
        val pending = jdbc.query(
            """
            SELECT id, booking_id, delivery_key, attempt_count
              FROM confirmation_outbox
             WHERE status = 'PENDING' AND attempt_count < ?
             ORDER BY id ASC
             LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                PendingConfirmation(
                    id = rs.getLong("id"),
                    bookingId = rs.getLong("booking_id"),
                    deliveryKey = rs.getString("delivery_key"),
                    attemptCount = rs.getInt("attempt_count"),
                )
            },
            maxAttempts,
            limit,
        )

        var acknowledged = 0
        var failed = 0
        var skipped = 0

        for (item in pending) {
            if (item.attemptCount >= maxAttempts) {
                skipped++
                continue
            }

            val failAfterAccept = demoSettings.isSimulateNotificationFailure()
            val payload = "Confirmation for booking ${item.bookingId}"

            appTx.execute {
                jdbc.update(
                    """
                    UPDATE confirmation_outbox
                       SET attempt_count = attempt_count + 1, updated_at = NOW()
                     WHERE id = ?
                    """.trimIndent(),
                    item.id,
                )
                jdbc.update(
                    """
                    INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                    VALUES (?, ?, 'ATTEMPT', ?)
                    """.trimIndent(),
                    item.bookingId,
                    item.deliveryKey,
                    "Dispatch attempt ${item.attemptCount + 1}",
                )
            }
            demoActivity.record(
                "DELIVERY_ATTEMPT",
                "Attempt ${item.attemptCount + 1} for booking ${item.bookingId} key=${item.deliveryKey}",
            )

            val result = try {
                gateway.send(item.deliveryKey, item.bookingId, payload, failAfterAccept)
            } catch (ex: Exception) {
                failed++
                appTx.execute {
                    jdbc.update(
                        """
                        INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                        VALUES (?, ?, 'ERROR', ?)
                        """.trimIndent(),
                        item.bookingId,
                        item.deliveryKey,
                        ex.message,
                    )
                }
                demoActivity.record(
                    "DELIVERY_ERROR",
                    "Error delivering booking ${item.bookingId}: ${ex.message}",
                )
                continue
            }

            if (result.timedOut || !result.acknowledged) {
                failed++
                appTx.execute {
                    jdbc.update(
                        """
                        INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                        VALUES (?, ?, 'TIMEOUT', 'Ack timeout after gateway accept')
                        """.trimIndent(),
                        item.bookingId,
                        item.deliveryKey,
                    )
                }
                demoActivity.record(
                    "DELIVERY_TIMEOUT",
                    "Timeout waiting for ack of booking ${item.bookingId}; remains PENDING (gateway may have accepted)",
                )
                // Booking stays accepted; outbox stays PENDING for retry with same delivery_key
                continue
            }

            appTx.execute {
                jdbc.update(
                    """
                    UPDATE confirmation_outbox
                       SET status = 'ACKNOWLEDGED', updated_at = NOW()
                     WHERE id = ?
                    """.trimIndent(),
                    item.id,
                )
                jdbc.update(
                    """
                    INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                    VALUES (?, ?, 'ACKNOWLEDGED', ?)
                    """.trimIndent(),
                    item.bookingId,
                    item.deliveryKey,
                    if (result.duplicate) "Deduplicated accept + ack" else "Accepted + ack",
                )
            }
            demoActivity.record(
                "DELIVERY_ACKED",
                "Confirmation acknowledged for booking ${item.bookingId}",
            )
            acknowledged++
        }

        return DispatchSummary(
            attempted = pending.size,
            acknowledged = acknowledged,
            failed = failed,
            skipped = skipped,
        )
    }

    private data class PendingConfirmation(
        val id: Long,
        val bookingId: Long,
        val deliveryKey: String,
        val attemptCount: Int,
    )
}
