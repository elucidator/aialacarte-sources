# Fake notification gateway contract

- The gateway accepts a **delivery key** supplied by the sender.
- Deduplication is by delivery key: accepting the same key twice records **one**
  durable message.
- The gateway may **accept and durably record** a message, then experience a
  **timeout before acknowledgement** is returned to the sender.
- A timeout after acceptance must not be confused with "never accepted".
- Retrieving acknowledgement is separate from acceptance.
- This is a simulator contract for the conference demo — not a claim about
  real-world email providers.
