#!/usr/bin/env python3
"""Restore correct session locking in an exercise-2 workspace."""
from __future__ import annotations

import pathlib
import re
import sys


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    path = root / "src/main/kotlin/com/conference/planner/booking/BookingService.kt"
    text = path.read_text()
    text = re.sub(
        r"\n    private fun maybePauseForRepro\(\) \{.*?\n    \}\n",
        "\n",
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace(
        """        // Availability checked without session lock / advisory coordination around insert.
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
""",
        """        val session = repo.findSessionForUpdate(sessionId)
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
""",
        1,
    )
    if "findSessionForUpdate" not in text:
        raise SystemExit("reference fix did not restore findSessionForUpdate")
    path.write_text(text)
    print("Applied exercise 2 reference fix")


if __name__ == "__main__":
    main()
