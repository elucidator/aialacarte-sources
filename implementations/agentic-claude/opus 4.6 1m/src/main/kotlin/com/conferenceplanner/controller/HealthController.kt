package com.conferenceplanner.controller

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val jdbc: JdbcTemplate) {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return try {
            jdbc.queryForObject("SELECT 1", Int::class.java)
            ResponseEntity.ok(mapOf("status" to "UP", "database" to "connected"))
        } catch (e: Exception) {
            ResponseEntity.status(503).body(mapOf("status" to "DOWN", "database" to e.message.orEmpty()))
        }
    }
}
