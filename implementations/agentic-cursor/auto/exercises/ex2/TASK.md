# Exercise 2 — Debugging: last-place overbooking

## Observed symptom

Under concurrent load, **Practical Kotlin** (Room B, capacity 20, one place left)
sometimes ends with **21 active bookings**. Alice and Bob can both receive HTTP 201
for that last place.

Duplicate-attendee booking for the same session still returns 409.
Replaying the same `Idempotency-Key` still returns the original booking.

## Your task

1. Reproduce the overbooking symptom reliably.
2. Find the coordination defect in the booking path.
3. Fix it so active bookings never exceed capacity, including when two app
   processes share one PostgreSQL database.
4. Keep existing idempotency and duplicate-attendee behaviour intact.

## Constraints

- Use PostgreSQL-backed coordination (not a process-local lock).
- Do not weaken or delete the project's capacity-related checks to "make green".
- Prefer a fix that also passes independent concurrent-request verification.

## Out of scope

UI redesign, notification changes, and availableOnly filtering.
