# Exercise 5 — Presenter notes

Wrong Compose healthcheck path: `/actuator/healthz` instead of `/health`.
Process starts; health stays unhealthy. Fix path; recreate app service in project
`conference-planner-ex5` only.

Acceptance: `presenter/exercises/ex5/verify-ex5.sh <workspace>`
