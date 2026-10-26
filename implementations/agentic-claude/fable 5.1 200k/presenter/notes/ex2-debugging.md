# Ex2 – Debugging: overbooking under concurrency (PRESENTER-ONLY)

**The fault.** In the exported workspace `PostgresBookingRepository.lockSession` (baseline:
`SELECT … FOR UPDATE` on the session row) is replaced by `findSession`, a plain `SELECT`.
Two transactions can then both count 39 < 40 and both insert. The partial unique index still
prevents duplicates by the same attendee and the request-key claim still prevents replays,
so only the capacity invariant breaks, matching the symptom in `TASK.md`. The exported
README has the "Invariants under concurrency" section and the two-process test removed;
`ConcurrencyTest` and `TwoProcessLastPlaceTest` are excluded.

**Reveal order.** 1) Ask how they reproduced it: a loop of parallel requests "sometimes"
shows it; the deterministic version needs to force the interleaving. 2) Show
`acceptance/ex2/LastPlaceInterleavingTest`: a helper connection holds
`LOCK TABLE bookings IN EXCLUSIVE MODE` (blocks INSERT, not SELECT), both bookers are fired,
the test polls `pg_stat_activity` until two backends wait on a lock (10 s bound), then
releases. 3) The fix is the row lock on the session (or a serializable/advisory alternative);
the fix must hold with two processes because coordination lives in PostgreSQL.

**Why the reproducer cannot deadlock with the correct code:** the first booker holds the
session row lock and waits on the table lock; the second waits on the row lock; the helper
holds only the table lock and is the one that releases. Nobody waits on the helper except
via locks the helper releases unconditionally after a bounded wait (finally-block rollback).

**Evidence bundle** (`exercises/ex2-debugging/evidence/`, exported with
`tools/export-evidence.sh`): generated from a real run of the reproducer against the faulty
export (`WRITE_EVIDENCE=1 presenter/tools/verify-exercises.sh ex2`): both HTTP 201s,
session 3 at 41/40 with `availablePlaces: -1`, the 41 active rows, activity log.

**Reference fix**: `reference-patches/ex2.patch` restores the baseline files.
**Acceptance**: reproducer fails on the export, passes after the patch; then the restored
`ConcurrencyTest` and `TwoProcessLastPlaceTest` pass.
