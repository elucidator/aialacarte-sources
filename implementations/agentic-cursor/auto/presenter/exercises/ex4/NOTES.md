# Exercise 4 — Presenter notes

## Defect (do not reveal to attendees)
Candidate uses a fresh delivery key on every retry. After accept+timeout, retry
accepts again → two `gateway_messages` for one booking.

## Review framing
Give attendees `GATEWAY-CONTRACT.md` only. Expected finding: unstable keys defeat
gateway deduplication across lost-ack retries.

## Acceptance
`presenter/exercises/ex4/verify-ex4.sh <ex4-workspace>`
