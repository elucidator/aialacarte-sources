#!/usr/bin/env python3
"""Restore stable delivery keys in an exercise-4 candidate tree."""
from __future__ import annotations

import pathlib
import sys

# Reuse the inverse of exercises/ex4/apply_candidate.py by re-applying baseline block
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[3] / "exercises" / "ex4"))


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    path = root / "src/main/kotlin/com/conference/planner/notify/Notification.kt"
    text = path.read_text()
    if "attemptKey" not in text:
        raise SystemExit("candidate marker attemptKey not found")
    text = text.replace(
        """            val failAfterAccept = demoSettings.isSimulateNotificationFailure()
            val payload = "Confirmation for booking ${item.bookingId}"
            val attemptKey = item.deliveryKey + "-attempt-" + (item.attemptCount + 1)

            appTx.execute {
                jdbc.update(
                    '''
                    UPDATE confirmation_outbox
                       SET attempt_count = attempt_count + 1, updated_at = NOW()
                     WHERE id = ?
                    '''.trimIndent(),
                    item.id,
                )
                jdbc.update(
                    '''
                    INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                    VALUES (?, ?, 'ATTEMPT', ?)
                    '''.trimIndent(),
                    item.bookingId,
                    attemptKey,
                    "Dispatch attempt ${item.attemptCount + 1}",
                )
            }
            demoActivity.record(
                "DELIVERY_ATTEMPT",
                "Attempt ${item.attemptCount + 1} for booking ${item.bookingId} key=$attemptKey",
            )

            val result = try {
                gateway.send(attemptKey, item.bookingId, payload, failAfterAccept)
""".replace("'''", '"""'),
        """            val failAfterAccept = demoSettings.isSimulateNotificationFailure()
            val payload = "Confirmation for booking ${item.bookingId}"

            appTx.execute {
                jdbc.update(
                    '''
                    UPDATE confirmation_outbox
                       SET attempt_count = attempt_count + 1, updated_at = NOW()
                     WHERE id = ?
                    '''.trimIndent(),
                    item.id,
                )
                jdbc.update(
                    '''
                    INSERT INTO delivery_attempts (booking_id, delivery_key, outcome, detail)
                    VALUES (?, ?, 'ATTEMPT', ?)
                    '''.trimIndent(),
                    item.bookingId,
                    item.deliveryKey,
                    "Dispatch attempt ${item.attemptCount + 1}",
                )
            }
            demoActivity.record(
                "DELIVERY_ATTEMPT",
                "Attempt ${item.attemptCount + 1} for booking ${item.bookingId} key=${item.deliveryKey}",
            )

            val result = try {
                gateway.send(item.deliveryKey, item.bookingId, payload, failAfterAccept)
""".replace("'''", '"""'),
        1,
    )
    if "attemptKey" in text:
        raise SystemExit("failed to remove attemptKey")
    path.write_text(text)
    print("Applied exercise 4 reference correction")


if __name__ == "__main__":
    main()
