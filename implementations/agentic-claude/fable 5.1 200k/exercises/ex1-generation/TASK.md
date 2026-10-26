# Exercise 1 – Code generation

Add an optional `availableOnly` filter to the session list.

## API

`GET /api/sessions?page=0&size=20&availableOnly=true|false`

- `availableOnly` is optional. Default `false`, which keeps the current behaviour exactly.
- `availableOnly=true` returns only sessions with at least one available place.
- Filter **before** pagination: `totalElements` is the size of the filtered set, and pages are
  pages of the filtered set.
- Anything other than `true` or `false` is rejected with `400` and the documented
  `VALIDATION_ERROR` shape.
- Availability reflects active bookings only. A place released by a cancellation makes the
  session available again.

## UI

Add a checkbox above the session table, labelled so that a reader understands what it
filters. Toggling it reloads the list through the new parameter. Unchecked is the default.

## Done when

- The default checks pass: `./gradlew test`
- Your own checks cover the new parameter, including the 400 case and pagination of the
  filtered set.
- The checkbox works in a browser.
