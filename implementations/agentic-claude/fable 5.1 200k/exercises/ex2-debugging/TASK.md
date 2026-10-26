# Exercise 2 – Debugging

## Symptom

During a rehearsal with several people booking at the same moment, the session
**Understanding Databases (Room C, capacity 40)** ended up with **41 active bookings**.
The list view showed `availablePlaces: -1`. Nobody received an error; every booker saw a
successful booking.

The people involved were different attendees. Nobody booked twice, and nobody replayed a
request. Cancellations were not involved.

An evidence bundle (HTTP responses, list output and database rows captured during the
rehearsal) can be requested from the presenter.

## Task

1. Reproduce the problem locally in a repeatable way. Timing-dependent one-off reproductions
   do not count; explain how your reproduction forces the problematic interleaving.
2. Find and fix the root cause.
3. Show that the fix holds under simultaneous requests, including with two application
   processes sharing one database.

## Constraints

- Keep the API contract and error codes as documented in the README.
- Keep the existing protections against duplicate bookings by the same attendee and against
  request replays.
- The default test suite (`./gradlew test`) must still pass.
