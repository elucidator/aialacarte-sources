# Exercise 3 – Refactoring

`BookingService` depends directly on the concrete `PostgresBookingRepository`. Extract an
interface for the repository so that the service depends on the abstraction, while keeping the
behaviour of the application identical.

## Task

1. Introduce a `BookingRepository` interface in the `booking` package and make
   `PostgresBookingRepository` implement it.
2. Change `BookingService` to depend on the interface only.
3. Do not change the API, the JSON error shape and codes, the transaction boundaries or the
   locking behaviour. The service must still work unchanged with two application processes
   sharing one database.

## Done when

- `./gradlew test` passes without modifying any existing test.
- `./gradlew twoProcessTest` passes.
- `BookingService` has no reference to `PostgresBookingRepository`.
