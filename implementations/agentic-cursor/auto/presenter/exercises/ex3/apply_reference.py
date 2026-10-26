#!/usr/bin/env python3
"""Exercise 3 reference: extract BookingRepository interface."""
from __future__ import annotations

import pathlib
import re
import sys

INTERFACE = '''package com.conference.planner.booking

interface BookingRepository {
    fun countSessions(): Long
    fun listSessions(limit: Int, offset: Int): List<SessionRow>
    fun findSessionForUpdate(sessionId: Long): SessionRow?
    fun findSession(sessionId: Long): SessionRow?
    fun attendeeExists(attendeeId: Long): Boolean
    fun findBookingRequest(attendeeId: Long, idempotencyKey: String): BookingRequestRow?
    fun hasActiveBooking(sessionId: Long, attendeeId: Long): Boolean
    fun insertBooking(sessionId: Long, attendeeId: Long): Long
    fun insertBookingRequest(attendeeId: Long, idempotencyKey: String, sessionId: Long, bookingId: Long)
    fun insertConfirmationOutbox(bookingId: Long, deliveryKey: String)
    fun findBooking(bookingId: Long): BookingRow?
    fun listActiveBookings(attendeeId: Long): List<BookingRow>
    fun cancelActiveBooking(bookingId: Long, attendeeId: Long): Int
    fun bookingOwnedBy(bookingId: Long, attendeeId: Long): Boolean
}
'''

METHODS = [
    "countSessions",
    "listSessions",
    "findSessionForUpdate",
    "findSession",
    "attendeeExists",
    "findBookingRequest",
    "hasActiveBooking",
    "insertBooking",
    "insertBookingRequest",
    "insertConfirmationOutbox",
    "findBooking",
    "listActiveBookings",
    "cancelActiveBooking",
    "bookingOwnedBy",
]


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    iface = root / "src/main/kotlin/com/conference/planner/booking/BookingRepository.kt"
    iface.write_text(INTERFACE)

    repo = root / "src/main/kotlin/com/conference/planner/booking/PostgresBookingRepository.kt"
    rtext = repo.read_text()
    rtext = rtext.replace(
        "class PostgresBookingRepository(\n    private val jdbc: JdbcTemplate,\n)",
        "class PostgresBookingRepository(\n    private val jdbc: JdbcTemplate,\n) : BookingRepository",
        1,
    )
    for name in METHODS:
        rtext = re.sub(
            rf"(?m)^(?!\s*override\s)(\s*)fun {name}\(",
            rf"\1override fun {name}(",
            rtext,
            count=1,
        )
    repo.write_text(rtext)

    service = root / "src/main/kotlin/com/conference/planner/booking/BookingService.kt"
    stext = service.read_text()
    stext = stext.replace(
        "private val repo: PostgresBookingRepository,",
        "private val repo: BookingRepository,",
        1,
    )
    service.write_text(stext)
    print("Applied exercise 3 reference refactor")


if __name__ == "__main__":
    main()
