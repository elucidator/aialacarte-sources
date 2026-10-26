# Ex3 – Refactoring: extract `BookingRepository` (PRESENTER-ONLY)

**Point of the exercise.** Behaviour-preserving change under a strong test net. Watch for:
tools that "improve" the SQL or transaction handling while extracting; interfaces that leak
`JdbcClient`; renaming public methods (breaks nothing in tests but widens the diff); moving
the lock semantics out of the contract's documentation.

**Reference fix** (`reference-patches/ex3.patch`): interface with the 12 methods, doc comment
stating the transactional/locking contract, `override` on the Postgres implementation,
service constructor takes the interface. No other changes.

**Acceptance**: `acceptance/ex3-structure.sh` (interface exists, implementation declares it,
service has no reference to the concrete class) plus `./gradlew test twoProcessTest`.
