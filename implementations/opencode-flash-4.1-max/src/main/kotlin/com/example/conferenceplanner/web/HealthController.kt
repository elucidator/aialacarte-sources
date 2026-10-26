package com.example.conferenceplanner.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val jdbc: JdbcTemplate) {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        try {
            jdbc.queryForObject("select 1", Int::class.java)
            ResponseEntity.ok(mapOf("status" to "UP", "database" to "UP"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "DOWN", "database" to "DOWN", "reason" to (e.message ?: "unavailable")))
        }
}
