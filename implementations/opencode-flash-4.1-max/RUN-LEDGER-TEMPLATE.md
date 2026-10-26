# Run ledger template

Use one row per exercise run. Copy this file before a talk and fill it in while
presenting; do not estimate numbers afterwards that were not actually observed.

## Run metadata

| Field | Value |
|-------|-------|
| Date | |
| Talk / event | |
| Presenter | |
| Baseline workspace hash (`git rev-parse HEAD` or `find . | shasum` helper) | |
| Workstation | |
| Docker / JDK | |

## Exercise runs

| # | Exercise | Tool + model + version | Prompt (verbatim or reference) | Workspace / export hash | Active human time | Elapsed wall time | Interventions (count + what) | Usage charge (currency) | Acceptance outcome | Notes |
|---|----------|------------------------|--------------------------------|-------------------------|-------------------|-------------------|-------------------------------|-------------------------|--------------------|-------|
| 1 | Generation | | | | | | | | | |
| 2 | Debugging | | | | | | | | | |
| 3 | Refactoring | | | | | | | | | |
| 4 | Review | | | | | | | | | |
| 5 | DevOps | | | | | | | | | |

## Column definitions

- **Active human time**: time the human actually worked on the task (timed).
- **Elapsed wall time**: clock time from start to acceptance (timed).
- **Interventions**: how many times the human corrected, reprompted or edited; one
  sentence on what.
- **Usage charge**: only if the tool exposes a per-run cost; otherwise mark `n/a`.
- **Acceptance outcome**: one of `passed`, `failed`, `partial (reason)`, measured with
  the same command for every tool (record the command here once):
  `_________________________`.

Do not fabricate measurements and do not compare vendors without actual runs recorded
in this ledger.
