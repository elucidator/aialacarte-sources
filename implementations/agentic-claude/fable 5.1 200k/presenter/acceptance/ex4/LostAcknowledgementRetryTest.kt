package dev.conferenceplanner.acceptance

import dev.conferenceplanner.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Reproducer for exercise 4. The gateway accepts the message but the acknowledgement is lost;
 * the retry must be recognised as a duplicate. With per-attempt delivery keys the gateway
 * accepts a second message for the same booking.
 */
class LostAcknowledgementRetryTest : IntegrationTestBase() {

    private fun dispatch() = http.tree(http.post("/api/demo/dispatch"))

    @Test
    fun `a retry after a lost acknowledgement does not produce a second accepted confirmation`() {
        val id = http.tree(http.book("alice", 1, UUID.randomUUID().toString()))["id"].asLong()

        http.post("/api/demo/gateway", """{"loseAcknowledgements": true}""")
        assertThat(dispatch()["timeouts"].asInt()).isEqualTo(1)
        http.post("/api/demo/gateway", """{"loseAcknowledgements": false}""")
        dispatch()

        val activity = http.activity()
        val accepted = activity["gatewayMessages"].values().map { it["delivery_key"].asText() }
        assertThat(accepted).describedAs("messages the gateway accepted for booking $id (must be exactly one)").hasSize(1)
        assertThat(activity["attempts"].values().map { it["outcome"].asText() }).containsExactly("TIMEOUT", "DUPLICATE_ACKNOWLEDGED")
        assertThat(activity["confirmations"][0]["status"].asText()).isEqualTo("DELIVERED")
    }
}
