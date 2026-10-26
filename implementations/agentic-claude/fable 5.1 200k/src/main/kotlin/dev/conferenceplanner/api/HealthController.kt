package dev.conferenceplanner.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 200 only when the application can actually talk to PostgreSQL. */
@RestController
class HealthController(private val jdbc: JdbcClient) {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        val databaseUp = runCatching { jdbc.sql("SELECT 1").query(Int::class.javaObjectType).single() == 1 }.getOrDefault(false)
        val status = if (databaseUp) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(status).body(
            mapOf("status" to if (databaseUp) "UP" else "DOWN", "database" to if (databaseUp) "UP" else "DOWN"),
        )
    }
}
