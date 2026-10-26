# Exercise 2: Debugging — Booking Concurrency Issue

## Symptom

When multiple attendees try to book the last available place at the same time, more bookings are accepted than the session's capacity allows.

For example, "Practical Kotlin" has a capacity of 20. With 19 existing bookings, two simultaneous requests from different attendees both succeed — resulting in 21 active bookings.

The duplicate-attendee check and idempotency-key replay both work correctly. Only the capacity check fails under concurrent requests from different attendees.

## Your Task

1. Identify the root cause of the overbooking.
2. Reproduce the issue (see evidence below or write your own reproducer).
3. Fix the bug so that concurrent booking attempts for the last place never exceed capacity.
4. Verify the fix passes both the reproducer and the existing test suite.

## Evidence

When running concurrent booking requests against the last place:

```
Thread 1: reads activeCount=19, capacity=20 → proceeds to insert
Thread 2: reads activeCount=19, capacity=20 → proceeds to insert
Both succeed → 21 active bookings
```

## Constraints

- The fix must work with two app processes sharing the same database.
- A process-local lock (synchronized, ReentrantLock) is not sufficient.
- Do not change the API contract or error codes.
- Existing tests must continue to pass.
