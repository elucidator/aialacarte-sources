# Fake gateway contract

The confirmation gateway used by this application is a local simulator with the
following contract:

- `deliver(deliveryKey, bookingId)` durably records the message before it returns, using
  the supplied delivery key as the deduplication identity.
- A delivery key that has already been accepted is deduplicated: no new message is
  recorded and the gateway reports a duplicate.
- The gateway may fail with a timeout *after* the message has been recorded and
  committed. The caller sees an error and cannot know whether the message was recorded.
- A recorded message is never rolled back because the caller's transaction rolls back;
  the gateway owns its own storage and transactions.
- The caller acknowledges the delivery only after the gateway call reports success.

Under this contract, the same booking may be retried multiple times. The gateway makes
no guarantees about retries that use different delivery keys.
