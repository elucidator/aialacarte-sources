#!/usr/bin/env python3
"""Apply exercise-4 candidate confirmation retry change into a staged export."""
from __future__ import annotations

import pathlib
import sys

TARGET = "src/main/kotlin/com/conference/planner/notify/Notification.kt"

OLD = """            val failAfterAccept = demoSettings.isSimulateNotificationFailure()
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
""".replace("'''", '"""')

NEW = """            val failAfterAccept = demoSettings.isSimulateNotificationFailure()
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
""".replace("'''", '"""')


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    path = root / TARGET
    text = path.read_text()
    if OLD not in text:
        raise SystemExit("expected dispatcher block not found for candidate patch")
    path.write_text(text.replace(OLD, NEW, 1))
    print(f"Applied candidate change to {path}")


if __name__ == "__main__":
    main()
