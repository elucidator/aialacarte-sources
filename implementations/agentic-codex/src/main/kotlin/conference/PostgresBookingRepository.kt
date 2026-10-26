package conference

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.util.UUID

@Repository
class PostgresBookingRepository(private val jdbc: JdbcTemplate, manager: PlatformTransactionManager) {
    private val transaction = TransactionTemplate(manager).apply { timeout = 15 }

    private fun booking(rs: ResultSet) = Booking(rs.getString("id"), rs.getLong("session_id"),
        rs.getString("title"), rs.getString("attendee"), rs.getString("status"))
    private val bookingSelect = "SELECT b.*, s.title FROM bookings b JOIN sessions s ON s.id=b.session_id"

    // Shared database lock excludes reset across all app processes, including notification dispatch.
    fun sharedDemoLock() { jdbc.execute("SELECT pg_advisory_xact_lock_shared(731)") }

    fun sessions(page: Int, size: Int): SessionPage = transaction.execute {
        // One statement gives items and total a common MVCC snapshot.
        val rows = jdbc.queryForList("""
            WITH counts AS (
                SELECT s.*, count(b.id)::int AS active
                FROM sessions s LEFT JOIN bookings b ON b.session_id=s.id AND b.status='ACTIVE'
                GROUP BY s.id
            ), selected AS (SELECT * FROM counts ORDER BY id LIMIT ? OFFSET ?)
            SELECT selected.*, (SELECT count(*) FROM counts) AS total
            FROM (SELECT 1) anchor LEFT JOIN selected ON true ORDER BY selected.id
        """.trimIndent(), size, page.toLong() * size)
        val items = rows.filter { it["id"] != null }.map {
            Session((it["id"] as Number).toLong(), it["title"] as String, it["room"] as String,
                (it["capacity"] as Number).toInt(), (it["active"] as Number).toInt(),
                (it["capacity"] as Number).toInt() - (it["active"] as Number).toInt())
        }
        SessionPage(items, page, size, (rows.first()["total"] as Number).toLong())
    }!!

    fun book(attendee: String, key: String, sessionId: Long): BookingResult = transaction.execute {
        sharedDemoLock()
        // Hash collisions only serialize unrelated requests; the durable key is still the composite PK.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "$attendee:$key")
        val previous = jdbc.queryForList("SELECT * FROM booking_requests WHERE attendee=? AND request_key=?", attendee, key)
        if (previous.isNotEmpty()) {
            val row = previous.first()
            if ((row["session_id"] as Number).toLong() != sessionId)
                conflict("KEY_REUSED", "This request key was used for a different session.")
            return@execute BookingResult(find(row["booking_id"] as String)!!, true)
        }
        val capacity = jdbc.queryForList("SELECT capacity FROM sessions WHERE id=? FOR UPDATE", sessionId)
            .firstOrNull()?.get("capacity") as Number? ?: missing("Session not found.")
        if (jdbc.queryForObject("SELECT count(*) FROM bookings WHERE attendee=? AND session_id=? AND status='ACTIVE'",
                Long::class.java, attendee, sessionId)!! > 0)
            conflict("ALREADY_BOOKED", "You already have an active booking for this session.")
        val active = jdbc.queryForObject("SELECT count(*) FROM bookings WHERE session_id=? AND status='ACTIVE'",
            Long::class.java, sessionId)!!
        if (active >= capacity.toLong()) conflict("SESSION_FULL", "This session has no available places.")
        val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO bookings(id, session_id, attendee, status) VALUES (?, ?, ?, 'ACTIVE')", id, sessionId, attendee)
        jdbc.update("INSERT INTO booking_requests VALUES (?, ?, ?, ?)", attendee, key, sessionId, id)
        jdbc.update("INSERT INTO confirmations(booking_id, delivery_key) VALUES (?, ?)", id, "confirmation:$id")
        activity("BOOKED", id, "Booking accepted for $attendee")
        BookingResult(find(id)!!, false)
    }!!

    fun find(id: String): Booking? = jdbc.query("$bookingSelect WHERE b.id=?", { rs, _ -> booking(rs) }, id).firstOrNull()
    fun bookings(attendee: String): List<Booking> = jdbc.query(
        "$bookingSelect WHERE b.attendee=? AND b.status='ACTIVE' ORDER BY b.created_at, b.id",
        { rs, _ -> booking(rs) }, attendee)

    fun cancel(attendee: String, id: String) { transaction.executeWithoutResult {
        sharedDemoLock()
        val owned = find(id)?.takeIf { it.attendee == attendee } ?: missing("Booking not found.")
        // The same session lock orders bookings and cancellations; UPDATE releases the place once.
        jdbc.queryForList("SELECT id FROM sessions WHERE id=? FOR UPDATE", owned.sessionId)
        val changed = jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=? AND status='ACTIVE'", id)
        if (changed == 1) activity("CANCELLED", id, "Booking cancelled for $attendee")
    } }

    fun activity(kind: String, id: String?, detail: String) {
        jdbc.update("INSERT INTO activity(kind, booking_id, detail) VALUES (?, ?, ?)", kind, id, detail)
    }
}
