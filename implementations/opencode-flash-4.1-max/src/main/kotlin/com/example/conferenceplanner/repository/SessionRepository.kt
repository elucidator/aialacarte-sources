package com.example.conferenceplanner.repository

import com.example.conferenceplanner.model.PageResponse
import com.example.conferenceplanner.model.SessionSummary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SessionRepository(private val jdbc: JdbcTemplate) {

    fun list(page: Int, size: Int): PageResponse<SessionSummary> {
        val total = jdbc.queryForObject("select count(*) from sessions", Long::class.java) ?: 0L
        val items = jdbc.query(
            """
            select s.id, s.title, s.room, s.capacity,
                   coalesce(b.active_bookings, 0) as active_bookings
            from sessions s
            left join (
                select session_id, count(*) as active_bookings
                from bookings
                where status = 'ACTIVE'
                group by session_id
            ) b on b.session_id = s.id
            order by s.id asc
            limit ? offset ?
            """.trimIndent(),
            { rs, _ ->
                val capacity = rs.getInt("capacity")
                val active = rs.getInt("active_bookings")
                SessionSummary(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = capacity,
                    activeBookings = active,
                    availablePlaces = capacity - active,
                )
            },
            size, page.toLong() * size,
        )
        return PageResponse(items = items, page = page, size = size, totalElements = total)
    }
}
