package conference

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

class GatewayTimeout : RuntimeException("Gateway acknowledgement was lost after acceptance.")

@Service
class FakeGateway(private val jdbc: JdbcTemplate, manager: PlatformTransactionManager) {
    private val independent = TransactionTemplate(manager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        timeout = 5
    }
    fun deliver(bookingId: String, deliveryKey: String) {
        val timeout = independent.execute {
            val mode = jdbc.queryForObject("SELECT mode FROM demo_settings WHERE id=1", String::class.java)!!
            val lost = mode == "lost-ack"
            jdbc.update("INSERT INTO gateway_attempts(delivery_key, booking_id, outcome) VALUES (?, ?, ?)",
                deliveryKey, bookingId, if (mode == "fail") "REJECTED" else if (lost) "TIMEOUT" else "ACKNOWLEDGED")
            jdbc.update("INSERT INTO activity(kind, booking_id, detail) VALUES ('ATTEMPT', ?, ?)", bookingId, deliveryKey)
            if (mode != "fail") {
                val accepted = jdbc.update("INSERT INTO gateway_messages(delivery_key, booking_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    deliveryKey, bookingId)
                if (accepted == 1) jdbc.update("INSERT INTO activity(kind, booking_id, detail) VALUES ('ACCEPTED', ?, ?)", bookingId, deliveryKey)
            }
            if (mode == "fail") "rejected" else if (lost) "timeout" else "ok"
        }!!
        // Throws AFTER the gateway's own transaction has committed. The sender cannot undo acceptance.
        if (timeout == "timeout") throw GatewayTimeout()
        if (timeout == "rejected") throw IllegalStateException("Simulated gateway rejection.")
    }
}

@Service
class ConfirmationDispatcher(private val jdbc: JdbcTemplate, private val gateway: FakeGateway,
                             private val repository: PostgresBookingRepository, manager: PlatformTransactionManager) {
    private val transaction = TransactionTemplate(manager).apply { timeout = 15 }
    fun dispatch(): Map<String, Int> = transaction.execute {
        repository.sharedDemoLock()
        val pending = jdbc.queryForList("""
            SELECT booking_id, delivery_key FROM confirmations WHERE NOT acknowledged
            ORDER BY booking_id LIMIT 20 FOR UPDATE SKIP LOCKED
        """.trimIndent())
        var acknowledged = 0
        for (row in pending) {
            val bookingId = row["booking_id"] as String
            val deliveryKey = row["delivery_key"] as String
            jdbc.update("UPDATE confirmations SET attempts=attempts+1 WHERE booking_id=?", bookingId)
            try {
                gateway.deliver(bookingId, deliveryKey)
                jdbc.update("UPDATE confirmations SET acknowledged=true WHERE booking_id=?", bookingId)
                repository.activity("ACKNOWLEDGED", bookingId, deliveryKey)
                acknowledged++
            } catch (e: GatewayTimeout) {
                repository.activity("PENDING", bookingId, "Acknowledgement lost; retry is available.")
            } catch (e: IllegalStateException) {
                repository.activity("PENDING", bookingId, "Gateway rejected delivery; retry is available.")
            }
        }
        mapOf("attempted" to pending.size, "acknowledged" to acknowledged)
    }!!
}
