package dev.conferenceplanner.notification

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

class GatewayTimeoutException(deliveryKey: String) :
    RuntimeException("Gateway timed out before acknowledging delivery key '$deliveryKey'")

data class GatewayAck(val deliveryKey: String, val duplicate: Boolean)

/**
 * Stand-in for an external notification provider.
 *
 * Contract: a message is accepted and durably recorded under its delivery key; the same key is
 * never accepted twice (deduplication). A timeout can happen AFTER acceptance, in which case the
 * caller never learns the outcome. The gateway owns its own transaction (REQUIRES_NEW), so the
 * caller cannot roll back an acceptance the gateway has already recorded.
 */
@Component
class FakeGateway(private val jdbc: JdbcClient, transactionManager: PlatformTransactionManager) {
    private val ownTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun deliver(deliveryKey: String, recipient: String, body: String): GatewayAck {
        val (accepted, loseAcknowledgement) = ownTransaction.execute {
            val inserted = jdbc.sql(
                """
                INSERT INTO gateway_messages (delivery_key, recipient, body) VALUES (:key, :recipient, :body)
                ON CONFLICT (delivery_key) DO NOTHING
                """,
            ).param("key", deliveryKey).param("recipient", recipient).param("body", body).update() == 1
            val lose = jdbc.sql("SELECT lose_acknowledgements FROM gateway_settings WHERE id = 1")
                .query(Boolean::class.javaObjectType).optional().orElse(false)
            inserted to lose
        }!!
        // The gateway transaction is committed at this point. Losing the acknowledgement now models
        // a network timeout after the provider has already recorded the message.
        if (loseAcknowledgement) throw GatewayTimeoutException(deliveryKey)
        return GatewayAck(deliveryKey, duplicate = !accepted)
    }

    fun setLoseAcknowledgements(lose: Boolean) {
        ownTransaction.executeWithoutResult {
            jdbc.sql("UPDATE gateway_settings SET lose_acknowledgements = :lose WHERE id = 1").param("lose", lose).update()
        }
    }

    fun losesAcknowledgements(): Boolean =
        jdbc.sql("SELECT lose_acknowledgements FROM gateway_settings WHERE id = 1")
            .query(Boolean::class.javaObjectType).optional().orElse(false)
}
