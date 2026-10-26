package dev.conferenceplanner.session

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class SessionSummary(
    val id: Long,
    val title: String,
    val room: String,
    val capacity: Int,
    val activeBookings: Int,
    val availablePlaces: Int,
)

data class PageResponse<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)

@Repository
class PostgresSessionRepository(private val jdbc: JdbcClient) {

    /** Stable order: sort_order, then id. Availability is derived from ACTIVE bookings. */
    fun list(page: Int, size: Int): PageResponse<SessionSummary> {
        val total = jdbc.sql("SELECT count(*) FROM sessions").query(Long::class.javaObjectType).single()
        val items = jdbc.sql(
            """
            SELECT s.id, s.title, s.room, s.capacity,
                   (SELECT count(*) FROM bookings b WHERE b.session_id = s.id AND b.status = 'ACTIVE') AS active_bookings
            FROM sessions s
            ORDER BY s.sort_order, s.id
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("limit", size)
            .param("offset", page.toLong() * size)
            .query { rs, _ ->
                val capacity = rs.getInt("capacity")
                val active = rs.getInt("active_bookings")
                SessionSummary(rs.getLong("id"), rs.getString("title"), rs.getString("room"), capacity, active, capacity - active)
            }
            .list()
        return PageResponse(items, page, size, total)
    }
}
