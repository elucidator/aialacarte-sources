package dev.conferenceplanner.demo

import dev.conferenceplanner.notification.ConfirmationDispatcher
import dev.conferenceplanner.notification.DispatchReport
import dev.conferenceplanner.notification.FakeGateway
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class GatewaySettingsBody(val loseAcknowledgements: Boolean)

/**
 * Local demo controls. The whole controller exists only under the `local-demo` profile; outside it
 * every path under /api/demo is a plain 404.
 */
@RestController
@Profile("local-demo")
class DemoController(
    private val seed: SeedData,
    private val dispatcher: ConfirmationDispatcher,
    private val gateway: FakeGateway,
    private val jdbc: JdbcClient,
) {
    @GetMapping("/api/demo/status")
    fun status(): Map<String, Any> = mapOf("enabled" to true, "loseAcknowledgements" to gateway.losesAcknowledgements())

    @PostMapping("/api/demo/reset")
    fun reset(): Map<String, Any> {
        seed.reset()
        return mapOf("reset" to true)
    }

    @PostMapping("/api/demo/gateway")
    fun gateway(@RequestBody body: GatewaySettingsBody): Map<String, Any> {
        gateway.setLoseAcknowledgements(body.loseAcknowledgements)
        return mapOf("loseAcknowledgements" to body.loseAcknowledgements)
    }

    @PostMapping("/api/demo/dispatch")
    fun dispatch(): DispatchReport = dispatcher.dispatchPending()

    @GetMapping("/api/demo/activity")
    fun activity(): Map<String, Any> = mapOf(
        "activity" to rows("SELECT id, happened_at, kind, message FROM demo_activity ORDER BY id DESC LIMIT 50"),
        "confirmations" to rows("SELECT booking_id, delivery_key, status, attempts, created_at, delivered_at FROM confirmations ORDER BY booking_id"),
        "attempts" to rows("SELECT id, booking_id, attempt_no, delivery_key, outcome, attempted_at FROM delivery_attempts ORDER BY id"),
        "gatewayMessages" to rows("SELECT delivery_key, recipient, body, accepted_at FROM gateway_messages ORDER BY accepted_at, delivery_key"),
        "loseAcknowledgements" to gateway.losesAcknowledgements(),
    )

    private fun rows(sql: String): List<Map<String, Any?>> = jdbc.sql(sql).query().listOfRows()
}
