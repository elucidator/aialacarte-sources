# Exercise 1: Code Generation — Available Sessions Filter

## Task

Add an optional `availableOnly` query parameter to `GET /api/sessions`.

### Requirements

1. `GET /api/sessions?availableOnly=true` returns only sessions with available places.
2. `GET /api/sessions?availableOnly=false` or omitting the parameter returns all sessions (current behaviour).
3. Invalid values (anything other than `true` or `false`) return `400` with error code `INVALID_FILTER`.
4. Filtering is applied before pagination: `totalElements` reflects the filtered count.
5. Availability is based on active bookings vs capacity, including places freed by cancellations.

### UI Change

Add a checkbox labelled "Show available only" above the sessions table. When checked, the API call includes `availableOnly=true`. Unchecked omits the parameter.

### Constraints

- Do not change the existing session ordering (by `sort_order`).
- Do not change any other API behaviour.
- Existing tests must continue to pass.

### Verification

- Test that a full session (Understanding Databases, 40/40) is excluded when `availableOnly=true`.
- Test that cancelling a booking then filtering shows the session with the freed place.
- Test that `availableOnly=false` and omitting the parameter both return all sessions.
- Test that `availableOnly=xyz` returns 400.
