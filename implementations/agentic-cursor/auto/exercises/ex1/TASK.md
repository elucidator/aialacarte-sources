# Exercise 1 — Generation: availableOnly filter

## Goal

Add optional filtering to the session list so attendees can hide full sessions.

## Task

1. Extend `GET /api/sessions` with an optional query parameter `availableOnly`.
   - Accept `true` or `false` (case-sensitive lowercase as typical for booleans in this API).
   - Default when omitted: `false` (preserve current behaviour).
   - Invalid values must return HTTP 400 with the project's JSON error shape.
2. When `availableOnly=true`, include only sessions where `availablePlaces > 0`.
   - Filter **before** pagination.
   - `totalElements` must reflect the filtered set.
3. Add a matching checkbox to the Sessions UI that toggles this parameter and reloads the list.
4. Availability must reflect active bookings, including places released by cancellation.

## Out of scope

Do not change booking, cancellation, notification, or seed behaviour.

## Done when

- Default listing matches the baseline seed counts.
- `availableOnly=true` omits Understanding Databases (0 places) and updates `totalElements`.
- Cancelling a booking that frees a place makes that session reappear when filtered.
- Automated checks for the filter behaviour pass.
