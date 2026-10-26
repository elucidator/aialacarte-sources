# Exercise 3: Refactoring — Extract Repository Interface

## Context

`BookingService` depends directly on the concrete `BookingRepository` class. This makes the service tightly coupled to the PostgreSQL implementation.

## Your Task

1. Extract a `BookingRepositoryInterface` (or similar) from `BookingRepository`.
2. Make `BookingService` depend on the interface instead of the concrete class.
3. Keep `BookingRepository` as the implementation, annotated with `@Repository`.

## Constraints

- Do not change the API contract, response shapes, or error codes.
- Do not change the SQL queries or transaction behaviour.
- All existing tests must pass without modification.
- The `SELECT ... FOR UPDATE` locking pattern must remain in the repository implementation.
- The interface should cover all methods used by `BookingService`.

## Verification

- All existing integration and concurrency tests pass.
- `BookingService` constructor accepts the interface type, not the concrete class.
- The concrete repository class implements the interface.
