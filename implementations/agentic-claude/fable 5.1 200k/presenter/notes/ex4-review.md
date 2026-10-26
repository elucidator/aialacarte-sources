# Ex4 – Review: per-attempt delivery keys (PRESENTER-ONLY)

**The defect.** The candidate `ConfirmationDispatcher` sends
`"${deliveryKey}-attempt-$attemptNo"` instead of the stored key. The gateway deduplicates by
key (see `review/GATEWAY-CONTRACT.md`), so after a lost acknowledgement the retry is a *new*
message: the gateway accepts it, and the attendee receives two confirmations. The candidate
also records the per-attempt key in `delivery_attempts`, so the demo panel makes the second
acceptance visible. The change description sells it as "better traceability".

**Expected review outcome:** request changes / reject. Key finding: the delivery key is the
idempotency token of the gateway contract and must be stable per booking across attempts and
restarts. Secondary findings: the rationale (distinguishing attempts) is already served by
`attempt_no` in `delivery_attempts`; the change has no test although `NotificationTest`
would have caught it (it is excluded from the export on purpose).

**Presenter reproducer** (`acceptance/ex4/LostAcknowledgementRetryTest`): book, lose
acknowledgement, dispatch (TIMEOUT), restore, dispatch. On the candidate the assertion
"messages the gateway accepted … must be exactly one" fails with two keys
`confirmation-booking-1000-attempt-1` and `…-attempt-2`. After `reference-patches/ex4.patch`
(baseline dispatcher restored) the second attempt is `DUPLICATE_ACKNOWLEDGED` and exactly one
message exists.
