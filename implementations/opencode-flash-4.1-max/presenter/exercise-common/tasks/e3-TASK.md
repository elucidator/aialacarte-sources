# Task: extract a repository interface

`BookingService` currently depends directly on the concrete PostgreSQL repository class
that contains the SQL for the booking flow.

Extract an interface for the persistence operations the service uses, and make the
service depend on that abstraction while the PostgreSQL implementation stays behind it.

Preserve, without any behaviour change:

- the HTTP API and its error semantics;
- the transaction boundaries and the cross-process concurrency guarantees;
- the existing checks, which must keep passing.

Run the application and its tests as described in `README.md`.
