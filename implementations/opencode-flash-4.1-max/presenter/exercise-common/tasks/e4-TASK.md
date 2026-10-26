# Task: review a proposed change to confirmation retries

A teammate proposes a change to the confirmation dispatcher. The proposal is in
`review/`:

- `review/base-to-candidate.diff` - what changes;
- `review/base/NotificationDispatcher.kt` - before;
- `review/candidate/NotificationDispatcher.kt` - after;
- `review/GATEWAY-CONTRACT.md` - the delivery contract the gateway guarantees.

Review the proposal against that contract. Deliver a short written review covering:

- any correctness problems you find, and why they matter;
- the behaviour you would expect under a lost acknowledgement;
- the correction you would ask for.

This is a review task: do not modify the running application unless explicitly asked.
No behavioural test suite is provided for this proposal; reason from the contract.
