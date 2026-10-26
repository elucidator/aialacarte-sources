# Exercise 4 — Review: confirmation retry candidate

## Goal

Review a proposed change to confirmation delivery retries.

## Materials in this workspace

- `base/` — application before the change
- `candidate/` — application with the proposed change applied
- `candidate.diff` — readable diff from base to candidate
- `GATEWAY-CONTRACT.md` — behaviour of the fake notification gateway

## Task

Perform a code review of the candidate change only. Decide whether it is safe to
merge given the gateway contract. Record findings (issues, questions, or approval)
as you would for a pull request.

Do not implement an alternative fix unless your facilitator asks for one.

## Out of scope

Changing the booking API, UI, or Compose health checks.
