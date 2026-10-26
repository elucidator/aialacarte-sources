package com.conference.planner.web

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Simple readiness endpoint used by Docker HEALTHCHECK.
 * Returns 200 only when the database answers a trivial query.
 */
@RestController
class HealthController(
    private val jdbc: JdbcTemplate,
) {
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return try {
            jdbc.queryForObject("SELECT 1", Int::class.java)
            ResponseEntity.ok(mapOf("status" to "UP"))
        } catch (ex: Exception) {
            ResponseEntity.status(503).body(
                mapOf(
                    "status" to "DOWN",
                    "detail" to (ex.message ?: "database unavailable"),
                ),
            )
        }
    }
}
