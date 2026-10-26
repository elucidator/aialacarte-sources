# Task: optional availability filter for the session list

Add an optional `availableOnly` query parameter to `GET /api/sessions`.

Requirements:

- `availableOnly=true|false`, default `false`; without the parameter the response must
  be unchanged.
- `availableOnly=true` returns only sessions with at least one available place.
- The filter is applied before pagination and `totalElements` is computed for the
  filtered set, not for all sessions.
- Invalid boolean values return HTTP 400 using the documented error shape.
- Availability reflects active bookings, so a place freed by cancellation becomes
  available again.
- Add a matching checkbox to the sessions view in the web UI; changing it re-queries
  the API and resets to the first page.
- Existing behaviour and existing checks must keep working.

Run the application and its tests as described in `README.md`.
