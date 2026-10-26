#!/usr/bin/env python3
"""Apply the exercise-2 faulty booking coordination change into a staged export."""
from __future__ import annotations

import pathlib
import sys

TARGET_REL = "src/main/kotlin/com/conference/planner/booking/BookingService.kt"

OLD = """        val session = repo.findSessionForUpdate(sessionId)
            ?: throw ApiException(404, "SESSION_NOT_FOUND", "Unknown session id: $sessionId")

        if (session.activeBookings >= session.capacity) {
            throw ApiException(409, "SESSION_FULL", "Session is at full capacity")
        }
        if (repo.hasActiveBooking(sessionId, attendeeId)) {
            throw ApiException(
                409,
                "DUPLICATE_ACTIVE_BOOKING",
                "Attendee already has an active booking for this session",
            )
        }

        return try {
            val bookingId = repo.insertBooking(sessionId, attendeeId)
"""

NEW = """        // Availability checked without session lock / advisory coordination around insert.
        val session = repo.findSession(sessionId)
            ?: throw ApiException(404, "SESSION_NOT_FOUND", "Unknown session id: $sessionId")

        if (session.activeBookings >= session.capacity) {
            throw ApiException(409, "SESSION_FULL", "Session is at full capacity")
        }
        if (repo.hasActiveBooking(sessionId, attendeeId)) {
            throw ApiException(
                409,
                "DUPLICATE_ACTIVE_BOOKING",
                "Attendee already has an active booking for this session",
            )
        }

        maybePauseForRepro()

        return try {
            val bookingId = repo.insertBooking(sessionId, attendeeId)
"""

HOOK_OLD = """class BookingService(
    private val repo: PostgresBookingRepository,
    private val demoActivity: DemoActivityService,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)
"""

HOOK_NEW = """class BookingService(
    private val repo: PostgresBookingRepository,
    private val demoActivity: DemoActivityService,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    private fun maybePauseForRepro() {
        val probe = System.getProperty("conference.fault.probe")
        if (probe.isNullOrBlank()) {
            return
        }
        val gate = java.nio.file.Path.of(probe)
        java.nio.file.Files.createDirectories(gate)
        java.nio.file.Files.writeString(gate.resolve("ready-" + Thread.currentThread().threadId()), "ready")
        val go = gate.resolve("go")
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (java.nio.file.Files.exists(go)) {
                return
            }
            Thread.sleep(5)
        }
    }
"""


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    path = root / TARGET_REL
    text = path.read_text()
    if OLD not in text:
        raise SystemExit("expected booking lock block not found")
    if HOOK_OLD not in text:
        raise SystemExit("expected BookingService header not found")
    text = text.replace(HOOK_OLD, HOOK_NEW, 1)
    text = text.replace(OLD, NEW, 1)
    path.write_text(text)
    print(f"Applied faulty coordination to {path}")


if __name__ == "__main__":
    main()
