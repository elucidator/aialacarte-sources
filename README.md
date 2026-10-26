# AI Coding à la Carte — implementation sources

Reference snapshots of the five application builds used in the talk **“AI Coding à la Carte: Tools, Models and Trade-offs for Everyday Development.”**

This repository contains the source trees, tests, build configuration, exercise definitions and presenter material from the runs. It is meant for inspection and reproduction, not as a benchmark result.

## Start here

1. Read [`implementations/Starting-prompt.md`](implementations/Starting-prompt.md) — the shared application brief given to every run.
2. Pick one implementation from the table below.
3. Read that implementation's `README.md` and `BUILD-ENVIRONMENT.md`.
4. Open `src/main/` for the application, `src/test/` for its tests, and `exercises/` for the follow-up tasks.
5. Use `presenter/` for the runbook, checks, reference patches and exercise notes.

## The five snapshots

| Directory | Harness | Run label as recorded |
|---|---|---|
| [`agentic-cursor/auto`](implementations/agentic-cursor/auto) | Cursor | Auto mode |
| [`agentic-claude/fable 5.1 200k`](implementations/agentic-claude/fable%205.1%20200k) | Claude Code | Fable 5.1, 200k context |
| [`agentic-claude/opus 4.6 1m`](implementations/agentic-claude/opus%204.6%201m) | Claude Code | Opus 4.6, 1m context |
| [`agentic-codex`](implementations/agentic-codex) | Codex CLI | GPT-6 family |
| [`opencode-flash-4.1-max`](implementations/opencode-flash-4.1-max) | opencode | deepseek-flash |

Model and harness labels are the labels reported by the runs. They were not independently verified as a controlled comparison.

## Prompts

The prompt documents at the root of `implementations/` explain how the material was produced:

- [`Starting-prompt.md`](implementations/Starting-prompt.md) — application brief, constraints and exercise expectations.
- [`Post prompt for report.md`](implementations/Post%20prompt%20for%20report.md) — prompt used to produce a measurement/report file after a build.
- [`debug-excersise-prompt.md`](implementations/debug-excersise-prompt.md) — debugging exercise brief.
- [`refactor-excersise-prompt.md`](implementations/refactor-excersise-prompt.md) — refactoring exercise brief.

The prompt filenames retain their original spelling.

## What a run contains

A typical implementation has:

- `src/main/` — Kotlin/Spring Boot application, resources, migrations and static UI.
- `src/test/` — integration and concurrency tests.
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle wrapper.
- `build.gradle.kts`, `settings.gradle.kts` — build configuration.
- `Dockerfile`, `docker-compose.yml` or `compose.yaml` — local runtime.
- `BUILD-ENVIRONMENT.md` — recorded tool and dependency versions.
- `exercises/` — task definitions and exercise setup files.
- `presenter/` — runbook, checks, reference patches and notes.

The exact layout differs slightly between runs; use the run's own README.

## Running a snapshot

Requirements:

- JDK 21
- Docker with Compose
- No global Gradle installation is required; each project includes a wrapper

For example:

```bash
cd implementations/agentic-cursor/auto
./gradlew test
docker compose up --build
```

Use the commands and ports documented in the selected run's `README.md` and `BUILD-ENVIRONMENT.md`. PostgreSQL is required for the integration and concurrency tests.

## What is intentionally not here

The upload excludes:

- generated Gradle and IDE output: `build/`, `.gradle/`, `.kotlin/`, `.idea/`
- generated exercise exports: `exports/`
- Node and Python caches: `node_modules/`, `__pycache__/`, `*.pyc`
- evidence folders and verification bundles
- run reports, completion reports, cost confirmations and run-ledger CSV files

Those files are evidence for the talk rather than source material. Some contained machine-specific paths and were therefore left out of this public repository.

## Caveat

These are **five build runs from one prompt**, not a controlled benchmark. They show what these setups produced in this programme; they do not establish a general ranking of tools, models or developers. The source is evidence of what is in the code, not proof of deployment, production use, security or performance.

The companion presentation and talk material live in [`elucidator/ai-alacarte-presentation`](https://github.com/elucidator/ai-alacarte-presentation).
