# Exercise 3 — Refactoring: extract booking repository interface

## Goal

`BookingService` currently depends on the concrete `PostgresBookingRepository`.
Extract an interface without changing external behaviour.

## Task

1. Introduce a repository interface that captures the methods `BookingService` needs.
2. Make `PostgresBookingRepository` implement that interface.
3. Inject the interface into `BookingService`.
4. Preserve API responses, error codes, transaction boundaries, and concurrency
   behaviour (including capacity and idempotency).
5. Keep the existing automated checks available and passing.

## Constraints

- Do not change HTTP routes or JSON shapes.
- Do not introduce a second persistence technology.
- Keep the refactor small and reviewable.

## Done when

Existing booking/session/notification tests still pass with no semantic changes.
