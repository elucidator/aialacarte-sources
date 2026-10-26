# Run ledger – one sheet per tool × exercise

Fill this in only from actual runs. Leave a field empty rather than estimating.

| Field | Value |
|-------|-------|
| Exercise | ex_ – |
| Workspace hash (sha256 of `MANIFEST.sha256`, printed by the export) | |
| Date / time started (local) | |
| Tool | |
| Model and version / build | |
| Mode (chat, agent, autonomous; approvals on/off) | |
| Prompt given (verbatim or file reference) | |
| Context provided beyond the workspace (evidence bundle, README, none) | |
| Elapsed wall-clock time (start → acceptance run) | |
| Active human time (typing, reading, deciding) | |
| Number of interventions (corrections, re-prompts, manual edits) | |
| What each intervention was | |
| Usage charge or tokens as reported by the tool | |
| Acceptance check result (command, pass/fail, log path) | |
| Side effects outside the task (unrelated edits, dependencies added) | |
| Observations worth telling | |

Acceptance commands per exercise:

- ex1: `./gradlew test --tests 'dev.conferenceplanner.acceptance.*'` after adding `presenter/acceptance/ex1/*.kt`; `./gradlew uiTest -Pbrowser=chrome --tests '*AvailableOnlyUiTest'`
- ex2: `./gradlew test --tests '*LastPlaceInterleavingTest'` after adding `presenter/acceptance/ex2/*.kt`; then restore `ConcurrencyTest`/`TwoProcessLastPlaceTest` and run `./gradlew test twoProcessTest`
- ex3: `presenter/acceptance/ex3-structure.sh && ./gradlew test twoProcessTest`
- ex4: review text compared with `presenter/notes/ex4-review.md`; reproducer `*LostAcknowledgementRetryTest`
- ex5: `docker compose -p conference-planner-ex5 up -d --build --wait && curl -fsS localhost:8085/health`
