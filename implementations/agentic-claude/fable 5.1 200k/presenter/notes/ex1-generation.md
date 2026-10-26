# Ex1 – Generation: `availableOnly` filter (PRESENTER-ONLY)

**Reveal.** Nothing hidden; the exercise is about how well a tool turns a small spec into
API + SQL + UI + tests. Watch for: filtering *after* pagination (wrong `totalElements`),
lenient boolean parsing (`yes`, `1`, `TRUE` accepted although the brief says 400), a
checkbox that changes the query but not `totalElements`, and tests that only cover the
seed state and not "book last place → session disappears; cancel → it reappears".

**Reference fix** (`reference-patches/ex1.patch`): CTE with the active count, filter in
`WHERE` before `COUNT(*)` and `LIMIT/OFFSET`; strict `true`/`false` parsing; checkbox
`#available-only` above the table; `app.js` passes `availableOnly=${checked}`.

**Acceptance** (`acceptance/ex1/`): API test with filtered pagination, default behaviour,
book/cancel round trip, 400 cases (`yes`, `1`, `TRUE`, empty); UI test checks the checkbox
hides session 3 and unchecking restores it.
