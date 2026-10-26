# Presenter runbook — AI Coding à la Carte (40 minutes)

Map of the five exercises to the talk. Reset between exercises with the demo panel
or a fresh export. Screenshot/recording fallbacks are listed when live UI is risky.

## Shared stage setup (5 min before doors)

```bash
docker compose -f docker-compose.db.yml up -d
./gradlew --init-script init-mirror.gradle.kts bootRun --args='--spring.profiles.active=local-demo'
```

Browser: http://localhost:8080 — projector zoom 125%+.

**Expected screen:** three sessions with 22/19/40 active; Alice selected; demo panel visible.

**Fallback:** slides with seed table screenshot `presenter/runbook/fallback-seed.png` (capture once).

---

## Exercise 1 — Generation (~7 min)

| Step | Action |
|------|--------|
| Export | `./scripts/export-exercise.sh 1 ~/talk/ex1` |
| Prompt | Add `availableOnly` + checkbox (TASK.md) |
| Screen | Checkbox off = 3 sessions; on = 2 (full hidden) |
| Reveal | Filter before pagination; `totalElements` filtered |
| Reset | N/A (separate workspace) |
| Fallback | API curl demo of `availableOnly=true` |

Acceptance: `presenter/exercises/ex1/verify-ex1.sh ~/talk/ex1`

---

## Exercise 2 — Debugging (~8 min)

| Step | Action |
|------|--------|
| Export | `./scripts/export-exercise.sh 2 ~/talk/ex2` |
| Brief | Symptom only (TASK.md); evidence in `presenter/evidence/ex2` |
| Screen | After repro: Practical Kotlin shows 21/20 |
| Reveal | Missing DB coordination between read and insert |
| Reset | `POST /api/demo/reset` after demo |
| Fallback | Show FaultReproIT failure output |

Acceptance: `presenter/exercises/ex2/verify-ex2.sh ~/talk/ex2`

---

## Exercise 3 — Refactoring (~6 min)

| Step | Action |
|------|--------|
| Export | `./scripts/export-exercise.sh 3 ~/talk/ex3` |
| Task | Extract repository interface; keep tests green |
| Screen | Same UI/API as baseline |
| Reveal | Interface + inject; no behaviour change |
| Fallback | Side-by-side `BookingService` constructor diff |

Acceptance: `presenter/exercises/ex3/verify-ex3.sh ~/talk/ex3`

---

## Exercise 4 — Review (~7 min)

| Step | Action |
|------|--------|
| Export | `./scripts/export-exercise.sh 4 ~/talk/ex4` |
| Materials | `candidate.diff` + GATEWAY-CONTRACT.md |
| Screen | Optional: dispatch with failure simulation twice |
| Reveal | After discussion: fresh keys ⇒ two gateway accepts |
| Fallback | Presenter `FreshKeyDefectIT` output |

Acceptance: `presenter/exercises/ex4/verify-ex4.sh ~/talk/ex4`

---

## Exercise 5 — DevOps (~7 min)

| Step | Action |
|------|--------|
| Export | `./scripts/export-exercise.sh 5 ~/talk/ex5` |
| Demo | `docker compose up --build -d` → unhealthy app |
| Reveal | Healthcheck path typo; fix to `/health` |
| Recovery | Recreate only project `conference-planner-ex5` |
| Fallback | `docker compose ps` screenshot of unhealthy |

Acceptance: `presenter/exercises/ex5/verify-ex5.sh ~/talk/ex5`

---

## Close

Return to baseline; book as Alice; enable simulate failure; dispatch; show one
gateway accept + timeout; dispatch again; still one accept, then ack. Emphasise
gateway dedup contract ≠ real email exactly-once.
