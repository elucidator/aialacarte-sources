package com.conference.planner.demo

import com.conference.planner.api.ActivityItem
import com.conference.planner.api.DemoStatus
import com.conference.planner.notify.ConfirmationDispatcher
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Local-demo controls. Enabled only with the local-demo Spring profile.
 */
@RestController
@RequestMapping("/api/demo")
@Profile("local-demo")
class DemoController(
    private val seedReset: SeedResetService,
    private val demoSettings: DemoSettingsRepository,
    private val demoActivity: DemoActivityService,
    private val dispatcher: ConfirmationDispatcher,
) {
    @PostMapping("/reset")
    fun reset(): Map<String, String> {
        seedReset.reset()
        return mapOf("status" to "ok")
    }

    @GetMapping("/status")
    fun status(): DemoStatus {
        val counts = seedReset.counts()
        return DemoStatus(
            simulateNotificationFailure = demoSettings.isSimulateNotificationFailure(),
            pendingConfirmations = counts["pendingConfirmations"] as Int,
            acceptedDeliveries = counts["acceptedDeliveries"] as Int,
            acknowledgements = counts["acknowledgements"] as Int,
            recentActivity = demoActivity.recent().map {
                ActivityItem(it.id, it.kind, it.message, it.createdAt)
            },
        )
    }

    @PostMapping("/simulate-failure")
    fun simulateFailure(@RequestBody body: Map<String, Boolean>): Map<String, Any> {
        val enabled = body["enabled"]
            ?: return mapOf("error" to "enabled required")
        demoSettings.setSimulateNotificationFailure(enabled)
        demoActivity.record(
            "DEMO_SETTING",
            "simulate_notification_failure=$enabled",
        )
        return mapOf("simulateNotificationFailure" to enabled)
    }

    @PostMapping("/dispatch")
    fun dispatch(): ConfirmationDispatcher.DispatchSummary = dispatcher.dispatchPending()
}

/** Always-present stub that returns 404 outside local-demo so the UI can detect capability. */
@RestController
@RequestMapping("/api/demo")
@Profile("!local-demo")
class DemoDisabledController {
    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(404).body(
            mapOf(
                "code" to "DEMO_DISABLED",
                "message" to "Demo panel requires spring profile local-demo",
            ),
        )
}
