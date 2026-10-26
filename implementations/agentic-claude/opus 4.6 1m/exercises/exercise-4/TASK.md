# Exercise 4: Code Review — Confirmation Retry Change

## Context

When a booking is accepted, a pending confirmation notification is created in the same transaction. A dispatcher delivers pending confirmations to a fake gateway.

The gateway accepts a delivery key and deduplicates on that key: if it has already accepted a message with that key, it returns the existing delivery rather than creating a new one. A timeout may occur after the gateway has accepted the message but before the sender receives the acknowledgement.

## Candidate Change

A colleague has proposed a change to the notification retry logic. Review the diff in `candidate/change.diff`.

The change modifies the `dispatch` method in `NotificationService` to generate a fresh delivery key on each retry attempt, instead of reusing the stable key stored with the notification.

## Your Task

Review the change for correctness. Consider:

1. What happens when the gateway accepts a delivery but the sender times out before acknowledgement?
2. What happens when the sender retries after such a timeout?
3. How many accepted messages does the gateway end up with per booking?
4. Does the change preserve the intended guarantee?

Write your review findings. You do not need to implement a fix.

## Reference Material

- The gateway deduplicates on the supplied delivery key.
- A timeout may occur after the gateway has durably accepted the message.
- The baseline uses a stable delivery key (`confirm-{bookingId}`) for all attempts.
