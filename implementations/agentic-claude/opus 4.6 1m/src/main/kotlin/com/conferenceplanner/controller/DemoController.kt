package com.conferenceplanner.controller

import com.conferenceplanner.model.ApiError
import com.conferenceplanner.service.NotificationService
import com.conferenceplanner.service.SeedService
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/demo")
@Profile("local-demo")
class DemoController(
    private val seedService: SeedService,
    private val notificationService: NotificationService
) {

    @PostMapping("/reset")
    fun reset(): ResponseEntity<Any> {
        seedService.resetToSeed()
        notificationService.setSimulateTimeout(false)
        return ResponseEntity.ok(mapOf("status" to "reset complete"))
    }

    @GetMapping("/activity")
    fun activity(): ResponseEntity<Any> {
        return ResponseEntity.ok(notificationService.getActivity())
    }

    @PostMapping("/dispatch")
    fun dispatch(): ResponseEntity<Any> {
        val results = notificationService.dispatchPending()
        return ResponseEntity.ok(mapOf("dispatched" to results))
    }

    @PostMapping("/simulate-timeout")
    fun simulateTimeout(@RequestParam enabled: Boolean): ResponseEntity<Any> {
        notificationService.setSimulateTimeout(enabled)
        return ResponseEntity.ok(mapOf("simulateTimeout" to enabled))
    }

    @GetMapping("/timeout-status")
    fun timeoutStatus(): ResponseEntity<Any> {
        return ResponseEntity.ok(mapOf("simulateTimeout" to notificationService.isSimulatingTimeout()))
    }
}
