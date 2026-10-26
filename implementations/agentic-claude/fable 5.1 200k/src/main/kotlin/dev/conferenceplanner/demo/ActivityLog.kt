package dev.conferenceplanner.demo

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** Append-only demo activity. Written inside the caller's transaction, so failed requests leave no entry. */
@Component
class ActivityLog(private val jdbc: JdbcClient) {
    fun record(kind: String, message: String) {
        jdbc.sql("INSERT INTO demo_activity (kind, message) VALUES (:kind, :message)")
            .param("kind", kind).param("message", message).update()
    }
}
