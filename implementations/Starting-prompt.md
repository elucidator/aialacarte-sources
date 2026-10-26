You are implementing Conference Planner, a small runnable application and a
set of isolated engineering exercises for a 40-minute conference talk called
“AI Coding à la Carte”. Build the actual project and verify it. Do not stop
after producing an architecture proposal or code snippets.

GOAL

Attendees browse conference sessions, book a place, see their bookings and
cancel them. The presenter uses this application to compare AI-assisted code
generation, debugging, refactoring, code review and DevOps automation.

Create a correct baseline first. Then prepare independent exercise exports
with specific missing enhancements or deliberate defects. Reference solutions
and presenter explanations must remain separate from exercise workspaces.
Do not introduce random defects or hidden faulty modes into the baseline.

WORKING APPROACH

- Briefly state the implementation plan and then implement it.
- Make reasonable choices without repeatedly asking for confirmation.
- Work within the supplied project directory and preserve unrelated files.
- Use local disposable infrastructure. Do not publish, push, send messages,
  contact real attendees, or deploy to an external environment.
- Finish a working baseline before adding exercise-generation machinery.
- If a dependency, runtime or permission is unavailable, document exactly what
  is blocked. Never report checks as passing unless you actually ran them.
- Keep the code small, readable and suitable for explaining on a projector.

TECHNICAL CONSTRAINTS

- Kotlin/JVM with JDK 21.
- Spring Boot, Gradle Kotlin DSL and a complete Gradle wrapper.
- PostgreSQL, including for integration tests of transactions and concurrency.
- Use straightforward SQL/JDBC and explicit transaction boundaries. Keep the
  baseline repository concrete so extracting its interface is a real exercise.
- Plain HTML, CSS and JavaScript served by the backend. No separate frontend
  framework or Node build is needed.
- Docker Compose for the app and database. Support a Mac with Apple Silicon
  and ordinary Linux machines. Do not require architecture-specific binaries.
- Select compatible stable dependency versions using official documentation
  where available, pin them, and record them in BUILD-ENVIRONMENT.md. Honour
  an exact version list if one is supplied with this prompt.
- Do not add cloud services, Kubernetes, payment processing, real email,
  production authentication, microservices or AI APIs.
- Initial dependency downloads are acceptable. Normal demos should work
  locally without internet once dependencies and images are available.

USER INTERFACE

Create a readable session list showing title, room, capacity, active bookings,
available places and a Book action. Provide a My bookings view with Cancel
actions. Include visible loading, success and error states. Use readable
typography and sufficient contrast for a conference projector.

Provide a local demo identity selector for Alice and Bob. Label it as demo
identity, not authentication. Use the selected identity consistently in API
requests. The backend must enforce booking and cancellation rules.

Provide a separate local demo panel for resetting seed data, viewing activity,
simulating notification failure and dispatching pending confirmations. Any
mutation/inspection endpoints for these controls must require an explicit
local-demo profile and be disabled outside it. Do not put reset controls into
the normal booking flow.

Seed three sessions in stable order:
1. Debugging Distributed Systems, Room A, capacity 30, 22 active bookings.
2. Practical Kotlin, Room B, capacity 20, 19 active bookings.
3. Understanding Databases, Room C, capacity 40, 40 active bookings.

Use fictional seed attendees. Alice and Bob initially have no bookings.
Keep IDs deterministic. A reset restores these exact counts and clears new
requests, delivery attempts and demo activity. Seed bookings should not create
pending notifications or race with ongoing demo requests.

API CONTRACT

- GET /api/sessions?page=0&size=20 returns an object with items, page, size and
  totalElements. Every item has id, title, room, capacity, activeBookings and
  availablePlaces. Use stable ordering, documented in the README. Page is
  zero-based; size is 1..100; invalid values return 400.
- The baseline does NOT implement availableOnly filtering. That is exercise 1.
- POST /api/bookings accepts a sessionId, X-Demo-Attendee header and an
  Idempotency-Key header. Return 201 with the new booking. Replaying a completed
  successful request returns the original booking with 200.
- GET /api/bookings returns active bookings for X-Demo-Attendee.
- DELETE /api/bookings/{id} cancels that attendee's booking and returns 204.
  Repeating cancellation of an owned booking returns 204 without freeing
  another place. For a missing booking or another attendee's booking return
    404. A new booking after cancellation needs a fresh request key.
- A full session, a different request attempting a second active booking for
  the same attendee/session, or reuse of a request key with a different payload
  returns 409. Missing identity/key or malformed input returns 400. Unknown
  session IDs return 404.
- Use a consistent JSON error shape, including a machine-readable code and a
  clear message. Document the chosen codes.
- GET /health returns 200 when the app and required database are ready, and a
  non-success status otherwise. The container health check must inspect this
  endpoint using a tool actually installed in the runtime image.

CORRECTNESS

The baseline must maintain these invariants under simultaneous requests:
1. Active bookings never exceed the session's capacity.
2. Each attendee has at most one active booking per session.
3. Cancellation releases a place once.
4. Replaying an attendee-scoped booking request key cannot create another
   booking. Concurrent replays also return the same booking. A replay after
   cancellation returns the original booking state and does not rebook.
5. Accepted bookings remain accepted if confirmation delivery fails.

Use database-backed coordination and constraints as appropriate. A process-
local lock is not sufficient: the baseline must work with two app processes
sharing the same PostgreSQL database. Keep transaction scopes clear and
explain them in the README.

NOTIFICATION SIMULATOR

Persist a pending confirmation with the accepted booking in the same
transaction. A bounded dispatcher delivers pending confirmations to a fake
gateway. Manual dispatch from the demo panel is sufficient; no broker is
needed. A failed dispatch remains pending and can be retried later.

The gateway accepts a stable delivery key and deduplicates it. It can accept
and durably record a message, then simulate a timeout before acknowledgement.
The sender must not be able to roll back that recorded gateway acceptance.
Model this with genuinely separate transaction ownership or an equivalently
clear isolated simulator. Do not merely throw before recording a delivery.

The baseline reuses the same delivery key for the same booking on every
attempt, including after restart. Repeated attempts produce one accepted
message. Make attempts, accepted deliveries and acknowledgements separately
visible in the demo activity panel. Bound retries and avoid slow backoff in
the stage demo. Explain that this guarantee depends on the fake gateway's
deduplication contract, not universal exactly-once real email delivery.

EXERCISE EXPORTS

Provide a documented command to export each exercise into a new empty
destination. Do not overwrite a non-empty destination. The output must be a
self-contained buildable workspace with code, wrapper, configuration, a
neutral README and its task brief. Include the base/candidate diff where
needed for review. Record a manifest with file hashes.

Do not copy presenter notes, reference fixes, fixture-generation code, hidden
acceptance checks, solved snapshots or revealing Git history into exports.
Use an explicit allowlist. Do not rely on ignore files to keep these secret.

Exercise 1: generation
Export the correct baseline without availableOnly filtering. The task is to
add optional availableOnly=true|false to GET /api/sessions and a matching UI
checkbox. Default false preserves behaviour. Filter before pagination and
compute totalElements for the filtered set. Invalid booleans return 400.
Availability reflects active bookings, including released cancellations.

Exercise 2: debugging
Prepare a narrowly faulty variant that checks availability and inserts without
adequate database coordination, allowing different attendees to overbook the
last place. Keep duplicate-attendee and request-replay protections intact.
Provide a symptom-only task brief and a separately supplied evidence bundle.
Prepare a deterministic reproducer using bounded coordination around the
faulty read/insert sequence, with explicit timeouts. Do not depend on arbitrary
sleeps. Keep test instrumentation out of the default runtime. A reference fix
must also pass independent concurrent-request checks; do not use a barrier
design that deadlocks a correctly serialised implementation.

Exercise 3: refactoring
Export the correct baseline with BookingService depending on a concrete
PostgreSQL repository. The task is to extract an interface while preserving
the API, error semantics and transaction/concurrency behaviour. Keep existing
checks available. Do not perform the refactor in this starting workspace.

Exercise 4: review
Prepare a compact candidate change to confirmation retries that uses a fresh
delivery key on every attempt. Provide its base version and a readable diff.
The task is review only. The reviewer receives the gateway contract: a timeout
may occur after acceptance and deduplication uses the supplied delivery key.
Do not reveal the defect or solution in comments, filenames or the task brief.
The presenter-only reproducer must show two accepted confirmations for one
booking after a lost acknowledgement. The booking itself remains unique.
The reference correction reuses a stable key and produces one acceptance.

Exercise 5: devops
Export the correct application with a narrowly wrong Compose health-check
path. The process starts but the health check fails. The task is to inspect,
correct, validate and demonstrate recovery locally. Use an isolated Compose
project name and disposable data. Recovery must affect only this exercise's
containers and volumes. No production or remote deployment is involved.

Keep each exercise independent. An attempted fix in one must not change any
other exercise or the baseline. Store presenter-only explanations, reference
patches and acceptance checks in a separate presenter directory. Validate that
reference patches apply cleanly to their intended snapshots.

TESTS AND VERIFICATION

Provide meaningful automated checks for listing/pagination, booking, full
capacity rejection, duplicate prevention, request replay, cancellation,
notification deduplication and health behaviour. Test transactions against
PostgreSQL, not an in-memory substitute with different locking semantics.

Verify the last-place case using two app processes sharing the same database.
Use bounded timeouts and clean up test resources. Distinguish deterministic
fault reproduction from independent correctness tests of the repaired code.

Test API and UI behaviour, including the availability checkbox in the exercise
1 reference solution. Use browser automation if available; otherwise report
the exact manual UI checks still needed. Do not claim browser verification
solely from passing API tests.

The baseline's normal checks must pass. Exercise verification should explicitly
assert the documented failure in a faulty starting snapshot and the correct
behaviour after its reference patch. A generic failing test suite is not proof
that an intended defect was reproduced. Do not disable or weaken acceptance
checks to make a solution pass.

DELIVERABLES

1. Working baseline source, complete build wrapper and pinned dependencies.
2. Database migrations, deterministic seed/reset support and fake gateway.
3. Dockerfile and Docker Compose configuration plus a normal host-JDK workflow.
4. README with exact setup, run, test, reset, stop and troubleshooting commands.
5. BUILD-ENVIRONMENT.md with resolved versions and required local software.
6. Five independent exercise exports or working export commands, neutral task
   briefs and file-hash manifests.
7. Presenter-only reference patches, reproduction instructions and acceptance
   checks, excluded from evaluation workspaces.
8. A presenter runbook mapping the five exercises to the talk, with expected
   screen state, reveal, reset and screenshot/recording fallback instructions.
9. An empty run-ledger template for tool/model/version, prompt, workspace hash,
   active human time, elapsed time, interventions, usage charge and acceptance.
   Do not fabricate measurements or compare vendors without actual runs.
10. A completion report listing what you built, exact verification commands
    actually run, their outcomes, and any unresolved limitation.

COMPLETION

The baseline should start from the documented commands, display the seed
sessions, accept/cancel bookings and visibly demonstrate deduplicated retries.
Exercise exports must reproduce their intended starting conditions. Complete
the implementation and available checks before reporting back. If something
cannot be verified in your environment, provide the prepared files and exact
remaining command, and clearly say it was not run.