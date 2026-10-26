# Notification gateway contract

The confirmation dispatcher hands messages to a notification gateway (`FakeGateway` in this
workspace stands in for the real provider). The provider's contract is:

1. **Acceptance is durable.** When the gateway accepts a message it records it immediately,
   in its own transaction. The caller cannot undo an acceptance.
2. **Deduplication by delivery key.** Every message carries a caller-supplied delivery key.
   A key that was accepted before is not accepted again; the gateway acknowledges it as a
   duplicate instead of sending a second message.
3. **Timeouts may happen after acceptance.** A call can fail with a timeout even though the
   gateway has already accepted and recorded the message. The caller cannot distinguish
   "not accepted" from "accepted, acknowledgement lost".
4. Retrying is the caller's responsibility. The gateway does not retry on its own.
