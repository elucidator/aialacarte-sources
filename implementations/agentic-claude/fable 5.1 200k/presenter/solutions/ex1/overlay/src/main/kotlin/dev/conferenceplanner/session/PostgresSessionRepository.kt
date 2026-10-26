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

    /**
     * Stable order: sort_order, then id. Availability is derived from ACTIVE bookings. With
     * availableOnly the filter is applied before counting and paging, so totalElements and the
     * pages describe the filtered set.
     */
    fun list(page: Int, size: Int, availableOnly: Boolean = false): PageResponse<SessionSummary> {
        val total = jdbc.sql("$COUNTED SELECT count(*) FROM counted WHERE (:availableOnly = FALSE OR active_bookings < capacity)")
            .param("availableOnly", availableOnly)
            .query(Long::class.javaObjectType).single()
        val items = jdbc.sql(
            """
            $COUNTED
            SELECT id, title, room, capacity, active_bookings
            FROM counted
            WHERE (:availableOnly = FALSE OR active_bookings < capacity)
            ORDER BY sort_order, id
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("availableOnly", availableOnly)
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

    private companion object {
        const val COUNTED = """
            WITH counted AS (
                SELECT s.id, s.title, s.room, s.capacity, s.sort_order,
                       (SELECT count(*) FROM bookings b WHERE b.session_id = s.id AND b.status = 'ACTIVE')::int AS active_bookings
                FROM sessions s
            )
        """
    }
}
