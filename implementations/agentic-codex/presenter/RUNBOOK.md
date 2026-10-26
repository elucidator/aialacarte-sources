# Presenter runbook — AI Coding à la Carte (40 minutes)

These files are presenter-only. Attendees receive only an export and TASK.md. Exercise 2 gets its evidence ZIP separately. Do not open reference patches or this directory in an attendee evaluation workspace. Never compare tools/vendors without real runs; the ledger starts empty.

## Before the room opens

1. Install JDK 21, Docker/Compose and Python. Warm dependencies with `./gradlew test bootJar`; warm the browser environment from the root README. Build the Docker app and pull PostgreSQL while online. Verify `./gradlew --offline test bootJar`.
2. Preserve existing containers. Choose new Compose project names and unused app/db ports. Keep each project's name/ports in terminal tabs. Do not use global prune or another project's volumes. Use the baseline's separate local database, not the database of an existing conference application on ports 8080/5432.
3. `python3 presenter/checks/verify-exports.py` audits allowlists, manifests, destination refusal and clean reference patch application. Generate each attendee workspace with `python3 presenter/export.py NUMBER NEW_EMPTY_DESTINATION`. The generated workspace README has neutral local commands. Copies are independent and contain no Git history, presenter code, fixes or private acceptance checks.
4. Run the full private validation on a disposable PostgreSQL database, sequentially:

```sh
python3 presenter/checks/verify-exercises.py --browser-python /tmp/conference-browser/bin/python --output /tmp/conference-validation-UNIQUE
python3 presenter/checks/verify-compose.py
```

The first command exports temporary starting/reference workspaces into a new destination, applies patches and builds them. It asserts the intended defect rather than treating any test failure as proof. The second creates a unique Compose project on dynamically chosen loopback ports, checks real unhealthy/healthy states, verifies curl in the runtime, and removes only its new resources. It checks that containers already running at its start are still running afterward.

5. Reserve readable browser windows at 1440×1000 or larger, increase browser zoom if needed, hide distracting tabs. Normal UI starts with Alice, 22/19/40 active bookings and 8/1/0 places. Reset from /demo.html, then return to the booking page. Fresh keys are required after cancellation; use reset between unrelated examples.
6. Preserve screenshots from `verification-output/screenshots/` and `verification-output/release-exercises/1-browser/`. Record a short local rehearsal showing booking/cancel and a lost-ack retry. Recording is a manual remaining preparation step; automated screenshots are already available. Keep screenshots/recordings on the laptop and show them if a runtime/network/browser fails; label playback as prerecorded.

## Stage sequence

| Minutes | Mode | Expected screen | Reveal and reset |
|---|---|---|---|
| 0–5 | Baseline | Three sessions, Alice/Bob empty, Practical Kotlin has one place | Book as Alice, switch Bob to see full, cancel as Alice to release once. Reset in separate panel. |
| 5–12 | Generation | Exercise 1 has three rows and no checkbox | Prompt from TASK.md. Reference checkbox hides Room C, fills Room B then removes it; cancellation restores it. API filtered total starts at 2 and pagination happens afterward. |
| 12–20 | Debugging | Symptom: Practical Kotlin reaches 21/20 under two attendees | Supply evidence ZIP separately. Bounded trigger gate reproduces only the starting variant. Reveal session row lock around read/count/insert; validate the repair with ordinary simultaneous requests from two JVMs. |
| 20–26 | Refactoring | BookingService constructor names PostgresBookingRepository | Extract BookingRepository; SQL and transaction ownership stay concrete. Diff is small. Run existing PostgreSQL and two-process checks. No interface is already present in the starting snapshot. |
| 26–33 | Review | Candidate diff and gateway contract; no task code edits | Timeout can happen after acceptance. Candidate retries generate another key, so the next attempt can accept a second message. One booking remains. Stable persisted key reference gives one acceptance over two attempts, including after restart. |
| 33–38 | DevOps | Java process ready but its new Compose app is unhealthy | Inspect configured probe and execute curl inside runtime. Correct /status to /health; recreate only this project's app. Observe healthy and book/cancel locally. |
| 38–40 | Close | Ledger columns and real acceptance evidence | Discuss what was measured in actual runs. Do not invent human time, elapsed time, usage costs or model superiority. |

## Reference and acceptance commands

Always apply patches to a fresh exported copy, never to the baseline or an attendee's original workspace. Patches are relative to workspace root:

```sh
python3 presenter/export.py 1 /tmp/conference-reference-1-UNIQUE
cd /tmp/conference-reference-1-UNIQUE
git apply --check /ABSOLUTE/BASELINE/presenter/reference-patches/exercise-1.patch
git apply /ABSOLUTE/BASELINE/presenter/reference-patches/exercise-1.patch
DB_URL=jdbc:postgresql://127.0.0.1:54329/conference ./gradlew --offline test bootJar
```

Substitute the exercise number for other references. `git apply` requires no repository or revealing history. Use a database reserved for acceptance; these checks reset it. Before applying, record the exported MANIFEST.json workspaceHash and SHA-256 of the patch. Manifest records the **starting** snapshot; edits naturally change its hashes.

Exercise 1 acceptance:

```sh
python3 presenter/checks/scenarios.py 1 /tmp/conference-reference-1-UNIQUE --reference
/tmp/conference-browser/bin/python scripts/browser-check.py /tmp/conference-reference-1-UNIQUE --available-only
```

Exercise 2 deterministic observation (starting variant only):

```sh
python3 presenter/export.py 2 /tmp/conference-observation-2-UNIQUE
cp presenter/checks/CapacityEvidenceTest.kt /tmp/conference-observation-2-UNIQUE/src/test/kotlin/conference/
cd /tmp/conference-observation-2-UNIQUE
./gradlew --offline test --tests conference.CapacityEvidenceTest bootJar
```

This test-only trigger blocks the two inserts at a PostgreSQL advisory gate after both requests have read availability. It observes two waiting locks with a 6-second deadline, always releases the gate, bounds task completion, removes the trigger/function, and resets the disposable database. It checks exactly 21 active bookings, two accepted IDs, original replay and duplicate rejection. No hook/profile/barrier is added to main source. Do not use this faulty-only test as acceptance for the correction: a correct implementation permits only one request to reach this gate. On a correct implementation the deadline expires and releases it, so the test fails diagnostically without deadlocking. Use the uninstrumented independent checker instead:

```sh
python3 /tmp/conference-reference-2-UNIQUE/scripts/process-check.py /tmp/conference-reference-2-UNIQUE
```

It releases a start barrier **before** HTTP requests, so a serialized implementation can progress freely. Twelve races use two real JVM processes and PostgreSQL; each race must yield one 201 and one 409, with active count exactly 20. Concurrent replay/cancellation, dispatch, restart and profile isolation are checked too.

Exercise 3: run JUnit and the same process checker on the interface reference. The interface contains only sessions/book/bookings/cancel; implementation retains explicit transactions and SQL. Avoid moving transaction scopes while extracting it.

Exercise 4 lost acknowledgement evidence:

```sh
python3 presenter/checks/scenarios.py 4 /PATH/TO/BUILT/STARTING/WORKSPACE
python3 presenter/checks/scenarios.py 4 /PATH/TO/BUILT/REFERENCE/WORKSPACE --reference
```

These assert two acceptances in the candidate and one in the corrected source, with two attempts, one sender acknowledgement and one booking. The process restarts between attempts; the gateway acceptance stays committed. Existing normal tests are expected to fail the candidate's deduplication assertion, but that generic failure is not our defect evidence. Use the explicit scenario counts. Review only: attendees report the finding; presenter applies the reference later.

Exercise 5 uses the isolated Compose verifier above. For manual recovery in its own workspace:

```sh
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-ex5-UNIQUE ps
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-ex5-UNIQUE logs app
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-ex5-UNIQUE exec app curl --fail --silent http://localhost:8080/health
# Apply reference patch from that workspace, then:
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-ex5-UNIQUE up -d --no-build --force-recreate app
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-ex5-UNIQUE ps
```

Keep the DB running so recovery retains the booking state; the YAML probe change needs no image rebuild. On completion, `docker compose -p conference-ex5-UNIQUE down -v` with the same port variables removes only the newly created exercise project. Never touch a project that existed before this exercise.

## Fallback and reset

If live generation/debugging takes too long, show the previously captured starting screen, the reference diff and real acceptance log. Say the live attempt is incomplete, then demonstrate the separate verified reference. Keep human interventions in the ledger. Do not silently replace candidate code with a fix.

Use /api/demo/reset or the separate demo panel to restore exact counts and clear delivery history; refresh all browser windows after reset. For failed infrastructure, use screenshots and the captured accepted/pending/acknowledged counts. Show the review timeout timeline on a slide: first attempt accepted → ack lost → retry; stable key deduplicates, fresh key does not. Fake-gateway deduplication is a specific simulator contract, not a real email guarantee.
