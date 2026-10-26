#!/usr/bin/env python3
"""Presenter-only fixture builder. Explicit files, no recursive source copy and no Git history."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST = [
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle.lockfile',
    'gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
    'Dockerfile', 'compose.yaml', '.dockerignore', '.gitignore', 'BUILD-ENVIRONMENT.md',
    'src/main/kotlin/conference/Application.kt', 'src/main/kotlin/conference/Models.kt',
    'src/main/kotlin/conference/ApiController.kt', 'src/main/kotlin/conference/BookingService.kt',
    'src/main/kotlin/conference/PostgresBookingRepository.kt', 'src/main/kotlin/conference/ConfirmationDispatcher.kt',
    'src/main/kotlin/conference/DemoController.kt', 'src/main/resources/application.yml',
    'src/main/resources/db/migration/V1__schema.sql', 'src/main/resources/db/migration/V2__seed.sql',
    'src/main/resources/static/index.html', 'src/main/resources/static/style.css',
    'src/main/resources/static/app.js', 'src/main/resources/static/demo.html', 'src/main/resources/static/demo.js',
    'src/test/kotlin/conference/PlannerIntegrationTest.kt', 'scripts/process-check.py',
    'scripts/process_check_import.py', 'scripts/browser-check.py'
]
NEUTRAL_README = '''# Conference Planner exercise workspace

Read TASK.md for the assigned work. JDK 21, Docker with Compose v2 and Python 3 are required. Kotlin/Spring Boot/JDBC use PostgreSQL; the plain browser UI needs no frontend build. All data and notifications are fictional. Versions, repository access and required tools are in BUILD-ENVIRONMENT.md. Dependencies are pinned in gradle.lockfile. They resolve from Maven Central; see BUILD-ENVIRONMENT.md for repository setup.

## Local run

Choose a unique project name and unused ports. Do not stop existing containers to free ports. Keep the same project name and ports for subsequent commands.

```sh
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-exercise-UNIQUE up -d --build
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-exercise-UNIQUE ps
```

Open http://127.0.0.1:18085 . Use the demo identity selector for Alice/Bob (not authentication). Session IDs 1/2/3 are ordered ascending and initially have capacities 30/20/40 and active counts 22/19/40. Alice and Bob have no bookings. Book sessions, switch to My bookings, cancel. The separate /demo.html panel can reset seeds and control the fake gateway. Controls and inspection require local-demo; omit that profile on host Java to disable /api/demo endpoints.

Host workflow (start only this project's db first):

```sh
DB_PORT=54335 docker compose -p conference-exercise-UNIQUE up -d db --wait
./gradlew bootJar
DB_URL=jdbc:postgresql://127.0.0.1:54335/conference PORT=18085 SPRING_PROFILES_ACTIVE=local-demo java -jar build/libs/conference-planner.jar
```

Database/user/password: conference. Stop host Java with Ctrl+C. Normal demos use cached dependencies/images without internet: prebuild before the event; ./gradlew --offline bootJar after warming dependencies.

## API and checks

GET /api/sessions?page=0&size=20 returns items/page/size/totalElements. Zero-based pages, size 1..100, stable ID ordering. Items include id/title/room/capacity/activeBookings/availablePlaces. POST /api/bookings accepts JSON sessionId and X-Demo-Attendee plus Idempotency-Key headers; 201 initial, 200 replay. GET /api/bookings lists identity's active bookings. DELETE /api/bookings/ID cancels; 204 including owned repeat cancellations. Missing/foreign bookings and unknown sessions are 404. Full sessions, duplicate active bookings and key/payload conflicts are 409. Missing or malformed input is 400. Errors have code/message; codes are INVALID_INPUT, NOT_FOUND, SESSION_FULL, ALREADY_BOOKED, KEY_REUSED, CONFLICT and INTERNAL_ERROR. A fresh booking after cancellation requires a fresh key. GET /health verifies app/database readiness.

Run checks sequentially against only this disposable database; they reset data:

```sh
DB_URL=jdbc:postgresql://127.0.0.1:54335/conference ./gradlew test bootJar
DB_URL=jdbc:postgresql://127.0.0.1:54335/conference python3 scripts/process-check.py
```

Checks cover listing, booking, capacity, duplicates, replay, cancellation, gateway retry and health. An assigned workspace may need changes before all checks pass. The process check starts two JVM children on unused ports, then cleans up those children. Existing PostgreSQL containers are untouched. Use the separate demo panel to see attempts, accepted confirmations and acknowledgements. Gateway modes: normal, fail, lost-ack. Manual dispatch is bounded to 20 pending confirmations per call. Failed confirmations remain pending.

For browser verification install Playwright 1.55.0 in a disposable Python environment and use local Chrome:

```sh
python3 -m venv /tmp/conference-browser
/tmp/conference-browser/bin/pip install --index-url https://pypi.org/simple playwright==1.55.0
DB_URL=jdbc:postgresql://127.0.0.1:54335/conference /tmp/conference-browser/bin/python scripts/browser-check.py
```

When adding the optional checkbox, pass --available-only to the browser script to exercise it. If browser tools are unavailable, manually inspect list counts and button states, switch identities, book/cancel, observe loading/error/success and inspect notification retry activity. API tests do not prove browser behaviour.

Stop/remove only this workspace's disposable resources:

```sh
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-exercise-UNIQUE stop
APP_PORT=18085 DB_PORT=54335 docker compose -p conference-exercise-UNIQUE down -v
```

For diagnosis, use this project’s ps/logs and inspect the configured health probe inside its app container. MANIFEST.json records SHA-256 for each starting file except itself and a combined workspace hash. Record your actual tooling, time, interventions and acceptance without inventing measurements.
'''


def mutate(path, old, new):
    text = path.read_text()
    if text.count(old) != 1: raise ValueError(f'Expected one match in {path}: {old}')
    path.write_text(text.replace(old,new))


def export(number, destination):
    destination = Path(destination).resolve()
    if destination == ROOT or ROOT.is_relative_to(destination): raise ValueError('Destination must not contain the source workspace.')
    if destination.exists() and (not destination.is_dir() or any(destination.iterdir())):
        raise ValueError('Destination must be new or empty; nothing was overwritten.')
    # Validate every source before making the destination.
    for name in ALLOWLIST:
        source = ROOT/name
        if not source.is_file() or source.is_symlink(): raise ValueError(f'Invalid allowlisted source: {name}')
    destination.mkdir(parents=True, exist_ok=True)
    for name in ALLOWLIST:
        target = destination/name; target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT/name,target)
    (destination/'README.md').write_text(NEUTRAL_README)
    shutil.copy2(ROOT/f'presenter/tasks/{number}.md', destination/'TASK.md')
    if number == 2:
        mutate(destination/'src/main/kotlin/conference/PostgresBookingRepository.kt',
               'SELECT capacity FROM sessions WHERE id=? FOR UPDATE', 'SELECT capacity FROM sessions WHERE id=?')
    elif number == 4:
        path = destination/'src/main/kotlin/conference/ConfirmationDispatcher.kt'
        base = path.read_text()
        mutate(path, 'val deliveryKey = row["delivery_key"] as String', 'val deliveryKey = java.util.UUID.randomUUID().toString()')
        import difflib
        review = destination/'review'; (review/'base').mkdir(parents=True)
        (review/'base/ConfirmationDispatcher.kt').write_text(base)
        (review/'change.diff').write_text(''.join(difflib.unified_diff(base.splitlines(True), path.read_text().splitlines(True),
            fromfile='a/src/main/kotlin/conference/ConfirmationDispatcher.kt', tofile='b/src/main/kotlin/conference/ConfirmationDispatcher.kt')))
    elif number == 5:
        mutate(destination/'compose.yaml', 'http://localhost:8080/health', 'http://localhost:8080/status')
    files = sorted(p for p in destination.rglob('*') if p.is_file())
    hashes = {str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    digest = hashlib.sha256(json.dumps(hashes, sort_keys=True, separators=(',',':')).encode()).hexdigest()
    (destination/'MANIFEST.json').write_text(json.dumps({'exercise':number,'algorithm':'SHA-256','workspaceHash':digest,'files':hashes},indent=2)+'\n')
    return digest

if __name__ == '__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('exercise', type=int, choices=range(1,6)); parser.add_argument('destination')
    args=parser.parse_args()
    try: print(export(args.exercise,args.destination))
    except ValueError as error: parser.exit(2,str(error)+'\n')
