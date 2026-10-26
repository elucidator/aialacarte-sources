#!/usr/bin/env python3
"""Apply exercise 1 reference solution into an exported workspace."""
from __future__ import annotations

import pathlib
import sys


def apply(root: pathlib.Path) -> None:
    service = root / "src/main/kotlin/com/conference/planner/booking/BookingService.kt"
    repo = root / "src/main/kotlin/com/conference/planner/booking/PostgresBookingRepository.kt"
    controller = root / "src/main/kotlin/com/conference/planner/api/BookingController.kt"
    html = root / "src/main/resources/static/index.html"
    js = root / "src/main/resources/static/js/app.js"

    text = service.read_text()
    old = """    fun listSessions(page: Int, size: Int): SessionPage {
        if (page < 0) {
            throw ApiException(400, "INVALID_PAGE", "page must be >= 0")
        }
        if (size < 1 || size > 100) {
            throw ApiException(400, "INVALID_SIZE", "size must be between 1 and 100")
        }
        val total = repo.countSessions()
        val items = repo.listSessions(size, page * size).map { it.toView() }
        return SessionPage(items = items, page = page, size = size, totalElements = total)
    }
"""
    new = """    fun listSessions(page: Int, size: Int, availableOnly: Boolean = false): SessionPage {
        if (page < 0) {
            throw ApiException(400, "INVALID_PAGE", "page must be >= 0")
        }
        if (size < 1 || size > 100) {
            throw ApiException(400, "INVALID_SIZE", "size must be between 1 and 100")
        }
        val total = repo.countSessions(availableOnly)
        val items = repo.listSessions(size, page * size, availableOnly).map { it.toView() }
        return SessionPage(items = items, page = page, size = size, totalElements = total)
    }
"""
    if old not in text:
        raise SystemExit("BookingService listSessions block not found")
    service.write_text(text.replace(old, new, 1))

    rtext = repo.read_text()
    old_count = """    fun countSessions(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM sessions", Long::class.java)!!
"""
    new_count = """    fun countSessions(availableOnly: Boolean = false): Long {
        val sql = if (availableOnly) {
            '''
            SELECT COUNT(*) FROM sessions s
             WHERE (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') < s.capacity
            '''.trimIndent()
        } else {
            "SELECT COUNT(*) FROM sessions"
        }
        return jdbc.queryForObject(sql, Long::class.java)!!
    }
""".replace("'''", '"""')
    if old_count not in rtext:
        raise SystemExit("countSessions not found")
    rtext = rtext.replace(old_count, new_count, 1)

    old_list = """    fun listSessions(limit: Int, offset: Int): List<SessionRow> =
        jdbc.query(
            '''
            SELECT s.id, s.title, s.room, s.capacity,
                   (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') AS active_bookings
              FROM sessions s
             ORDER BY s.sort_order ASC, s.id ASC
             LIMIT ? OFFSET ?
            '''.trimIndent(),
            { rs, _ ->
                val capacity = rs.getInt("capacity")
                val active = rs.getInt("active_bookings")
                SessionRow(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = capacity,
                    activeBookings = active,
                )
            },
            limit,
            offset,
        )
""".replace("'''", '"""')
    new_list = """    fun listSessions(limit: Int, offset: Int, availableOnly: Boolean = false): List<SessionRow> {
        val where = if (availableOnly) {
            '''
             WHERE (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') < s.capacity
            '''.trimIndent()
        } else {
            ""
        }
        return jdbc.query(
            '''
            SELECT s.id, s.title, s.room, s.capacity,
                   (SELECT COUNT(*) FROM bookings b
                     WHERE b.session_id = s.id AND b.status = 'ACTIVE') AS active_bookings
              FROM sessions s
            $where
             ORDER BY s.sort_order ASC, s.id ASC
             LIMIT ? OFFSET ?
            '''.trimIndent(),
            { rs, _ ->
                val capacity = rs.getInt("capacity")
                val active = rs.getInt("active_bookings")
                SessionRow(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    room = rs.getString("room"),
                    capacity = capacity,
                    activeBookings = active,
                )
            },
            limit,
            offset,
        )
    }
""".replace("'''", '"""')
    if old_list not in rtext:
        raise SystemExit("listSessions not found")
    rtext = rtext.replace(old_list, new_list, 1)
    repo.write_text(rtext)

    ctext = controller.read_text()
    cold = """    @GetMapping("/sessions")
    fun sessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): SessionPage = bookingService.listSessions(page, size)
"""
    cnew = """    @GetMapping("/sessions")
    fun sessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) availableOnly: String?,
    ): SessionPage {
        val filter = when (availableOnly) {
            null -> false
            "true" -> true
            "false" -> false
            else -> throw ApiException(400, "INVALID_AVAILABLE_ONLY", "availableOnly must be true or false")
        }
        return bookingService.listSessions(page, size, filter)
    }
"""
    if cold not in ctext:
        raise SystemExit("controller sessions mapping not found")
    controller.write_text(ctext.replace(cold, cnew, 1))

    h = html.read_text()
    needle = """      <div class="panel-head">
        <h2 id="sessions-heading">Sessions</h2>
        <button type="button" id="refresh-sessions" class="secondary">Refresh</button>
      </div>
"""
    repl = """      <div class="panel-head">
        <h2 id="sessions-heading">Sessions</h2>
        <div style="display:flex;gap:1rem;align-items:center">
          <label class="check"><input type="checkbox" id="available-only"> Available only</label>
          <button type="button" id="refresh-sessions" class="secondary">Refresh</button>
        </div>
      </div>
"""
    if needle not in h:
        raise SystemExit("sessions panel head not found")
    html.write_text(h.replace(needle, repl, 1))

    j = js.read_text()
    j_old = '      const res = await fetch("/api/sessions?page=0&size=20");\n'
    j_new = (
        '      const availableOnly = document.getElementById("available-only")?.checked ? "true" : "false";\n'
        '      const res = await fetch("/api/sessions?page=0&size=20&availableOnly=" + availableOnly);\n'
    )
    if j_old not in j:
        raise SystemExit("sessions fetch not found")
    j = j.replace(j_old, j_new, 1)
    j = j.replace(
        'document.getElementById("refresh-sessions").addEventListener("click", loadSessions);',
        'document.getElementById("refresh-sessions").addEventListener("click", loadSessions);\n'
        '  document.getElementById("available-only").addEventListener("change", loadSessions);',
        1,
    )
    js.write_text(j)
    print("Applied exercise 1 reference solution")


if __name__ == "__main__":
    apply(pathlib.Path(sys.argv[1]))
