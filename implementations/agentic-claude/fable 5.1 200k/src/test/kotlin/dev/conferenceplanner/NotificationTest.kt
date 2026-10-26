package dev.conferenceplanner

import dev.conferenceplanner.notification.FakeGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class NotificationTest : IntegrationTestBase() {

    @Autowired lateinit var gateway: FakeGateway
    @Autowired lateinit var jdbc: JdbcClient
    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private fun dispatch() = http.tree(http.post("/api/demo/dispatch"))
    private fun loseAcks(lose: Boolean) = http.post("/api/demo/gateway", """{"loseAcknowledgements": $lose}""")

    @Test
    fun `a booking queues a pending confirmation and seed bookings do not`() {
        assertThat(http.activity()["confirmations"].size()).isZero()
        val id = http.tree(http.book("alice", 1, UUID.randomUUID().toString()))["id"].asLong()
        val confirmations = http.activity()["confirmations"]
        assertThat(confirmations.size()).isEqualTo(1)
        assertThat(confirmations[0]["booking_id"].asLong()).isEqualTo(id)
        assertThat(confirmations[0]["status"].asText()).isEqualTo("PENDING")
        assertThat(confirmations[0]["delivery_key"].asText()).isEqualTo("confirmation-booking-$id")
    }

    @Test
    fun `dispatch delivers pending confirmations once`() {
        http.book("alice", 1, UUID.randomUUID().toString())
        http.book("bob", 2, UUID.randomUUID().toString())

        val report = dispatch()
        assertThat(report["processed"].asInt()).isEqualTo(2)
        assertThat(report["acknowledged"].asInt()).isEqualTo(2)

        val activity = http.activity()
        assertThat(activity["confirmations"].values().map { it["status"].asText() }).containsOnly("DELIVERED")
        assertThat(activity["gatewayMessages"].size()).isEqualTo(2)
        assertThat(activity["attempts"].values().map { it["outcome"].asText() }).containsOnly("ACKNOWLEDGED")

        // Nothing left to do.
        assertThat(dispatch()["processed"].asInt()).isZero()
    }

    @Test
    fun `a lost acknowledgement keeps the confirmation pending and the retry is deduplicated`() {
        val id = http.tree(http.book("alice", 1, UUID.randomUUID().toString()))["id"].asLong()

        loseAcks(true)
        val first = dispatch()
        assertThat(first["timeouts"].asInt()).isEqualTo(1)
        var activity = http.activity()
        assertThat(activity["confirmations"][0]["status"].asText()).isEqualTo("PENDING")
        assertThat(activity["confirmations"][0]["attempts"].asInt()).isEqualTo(1)
        assertThat(activity["attempts"].values().map { it["outcome"].asText() }).containsExactly("TIMEOUT")
        // The gateway recorded the message even though the sender saw a timeout (invariant 5 + contract).
        assertThat(activity["gatewayMessages"].size()).isEqualTo(1)
        assertThat(http.tree(http.get("/api/bookings", "alice")).values().map { it["id"].asLong() }).containsExactly(id)

        loseAcks(false)
        val second = dispatch()
        assertThat(second["duplicates"].asInt()).isEqualTo(1)
        activity = http.activity()
        assertThat(activity["confirmations"][0]["status"].asText()).isEqualTo("DELIVERED")
        assertThat(activity["confirmations"][0]["attempts"].asInt()).isEqualTo(2)
        assertThat(activity["attempts"].values().map { it["outcome"].asText() }).containsExactly("TIMEOUT", "DUPLICATE_ACKNOWLEDGED")
        assertThat(activity["attempts"].values().map { it["delivery_key"].asText() }.toSet()).containsExactly("confirmation-booking-$id")
        assertThat(activity["gatewayMessages"].size()).isEqualTo(1)
    }

    @Test
    fun `retries are bounded`() {
        http.book("alice", 1, UUID.randomUUID().toString())
        loseAcks(true)
        repeat(7) { dispatch() }
        val activity = http.activity()
        assertThat(activity["attempts"].size()).isEqualTo(5)
        assertThat(activity["confirmations"][0]["status"].asText()).isEqualTo("PENDING")
        assertThat(activity["gatewayMessages"].size()).isEqualTo(1)
    }

    @Test
    fun `gateway acceptance survives a rollback of the caller's transaction`() {
        val key = "rollback-probe-${UUID.randomUUID()}"
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            gateway.deliver(key, "alice", "probe")
            status.setRollbackOnly()
        }
        val stored = jdbc.sql("SELECT count(*) FROM gateway_messages WHERE delivery_key = :key").param("key", key)
            .query(Int::class.javaObjectType).single()
        assertThat(stored).isEqualTo(1)
    }
}
