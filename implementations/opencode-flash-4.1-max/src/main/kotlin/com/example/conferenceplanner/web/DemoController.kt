package com.example.conferenceplanner.web

import com.example.conferenceplanner.model.ActivityEntry
import com.example.conferenceplanner.model.DemoState
import com.example.conferenceplanner.model.DispatchSummary
import com.example.conferenceplanner.model.NotificationView
import com.example.conferenceplanner.model.ToggleRequest
import com.example.conferenceplanner.repository.DemoRepository
import com.example.conferenceplanner.repository.NotificationRepository
import com.example.conferenceplanner.service.DemoService
import com.example.conferenceplanner.service.NotificationDispatcher
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Local demo controls. The whole controller only exists while the explicit
 * 'local-demo' profile is active; outside that profile these routes are absent
 * (404) and the normal booking flow is unaffected.
 */
@RestController
@RequestMapping("/api/demo")
@Profile("local-demo")
class DemoController(
    private val demoService: DemoService,
    private val dispatcher: NotificationDispatcher,
    private val notificationRepository: NotificationRepository,
    private val demoRepository: DemoRepository,
) {

    @PostMapping("/reset")
    fun reset(): Map<String, String> {
        demoService.reset()
        return mapOf("status" to "reset")
    }

    @GetMapping("/state")
    fun state(): DemoState = demoService.state()

    @GetMapping("/activity")
    fun activity(@RequestParam(defaultValue = "50") limit: Int): List<ActivityEntry> {
        validateLimit(limit)
        return demoRepository.listActivity(limit)
    }

    @GetMapping("/notifications")
    fun notifications(@RequestParam(defaultValue = "50") limit: Int): List<NotificationView> {
        validateLimit(limit)
        return notificationRepository.listForPanel(limit)
    }

    @PostMapping("/simulate-notification-failure")
    fun simulateFailure(@RequestBody request: ToggleRequest): DemoState {
        demoService.setFailureSimulation(request.enabled)
        return demoService.state()
    }

    @PostMapping("/dispatch")
    fun dispatch(): DispatchSummary = dispatcher.dispatchPending()

    private fun validateLimit(limit: Int) {
        if (limit < 1 || limit > 200) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit must be between 1 and 200 but was $limit")
        }
    }
}
