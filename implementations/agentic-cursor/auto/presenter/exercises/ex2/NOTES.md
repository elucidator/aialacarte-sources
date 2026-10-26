# Exercise 2 — Presenter notes

## Defect
Availability is read without `findSessionForUpdate` (no advisory lock / row lock)
before insert, so two attendees can take the last place.

## Evidence bundle (separate from attendee export)
See `presenter/evidence/ex2/`.

## Repro
`FaultReproIT` uses `conference.fault.probe` file gate (bounded 5s). Default runtime
is unaffected when the property is unset.

## Fix
Restore `repo.findSessionForUpdate(sessionId)` and remove the repro pause hook.

## Acceptance
`presenter/exercises/ex2/verify-ex2.sh <workspace>`
