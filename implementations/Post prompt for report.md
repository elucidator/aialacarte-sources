
# GOAL

Create `report.md` in the project root that another human can paste into a talk or comparison ledger. Measure what you can; never invent tokens, cost, timings, or fix counts.

REPORT SECTIONS (in this order)

1. Single-prompt summary table
    
    - State how many _implementation_ user prompts produced the deliverable (usually 1 if the app was built from one spec).
    - Separate any later “reporting only” prompts from implementation prompts.
    - Active agent time excluding time waiting for the human (wall clock of agent turns only).
    - Human interventions during the build (count of mid-build steering prompts; 0 if the agent self-corrected).
    - Model / harness name if known.
2. Duration and token / cost usage
    
    - Active time excluding waiting for the user.
    - Token totals available in this harness (input, cache read/write if present, output).
    - If cost is not exposed, say so and point to where the human can find official billing.
    - List caveats (e.g. parent-only vs subagents, missing turns, log source path).
3. Lines of code and package size
    
    - Lines of application source vs test source (state exact paths counted, e.g.`src/main` vs `src/test`).
    - Break down by language/filetype when useful (Kotlin, SQL, HTML/CSS/JS, YAML, etc.).
    - File counts for main vs test.
    - Runnable package size _including runtime dependencies_ (for this stack: Spring Boot fat/`bootJar` size; also note nested lib size / jar count if easy).
    - Call out what is _not_ in that package (JDK, database server/image, etc.).
    - If a container image was built, optional image sizes.
4. Tools and scripts used to implement this
    
    - Agent/IDE tools actually used (shell, edit, browse, subagents, skills).
    - Local CLI/runtime (JDK, Gradle, Docker, curl, python, etc.) with versions when cheap to read.
    - Project scripts authored or relied on (export, verify, apply patches, compose files).
    - Environment workarounds (mirrors, offline caches).
    - Explicitly list notable things _not_ used.
5. Fixes after initial app generation
    
    - Count repair iterations after the first full source tree write until baseline green and (if applicable) exercise acceptance green.
    - Split: baseline build/test/run fixes vs exercise/presenter tooling fixes.
    - Numbered list: symptom → fix (one line each). Do not count planned exercise defects as “fixes.”
    - Note what landed correctly on first try.

RULES

- Prefer running measurement commands over estimating (wc, du, jar inspect, build tool, usage APIs/logs).
- If a metric cannot be obtained, write “not available” and the exact reason / remaining command.
- Do not fabricate vendor comparisons or currency cost.
- Keep the tone factual and table-friendly; suitable for a projector.
- Write only `report.md` unless you must create a tiny helper to gather metrics.

OPTIONAL ONE-LINE CLAIM

End with a short honest claim the presenter can say aloud, e.g. whether this was a single implementation prompt, active minutes, and that fix iterations were agent-driven.


