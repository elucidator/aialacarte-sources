# Change under review: per-attempt delivery keys for confirmation retries

**Author's description**

> Operations asked to be able to tell, from the attempts table alone, which delivery attempt
> actually reached the notification gateway. This change derives the delivery key for each
> attempt from the booking's key and the attempt number, records that key with the attempt,
> and includes it in the activity log. Retries are therefore never confused with an earlier
> attempt at the gateway.

Files:

- `candidate.diff` – the change as a unified diff
- `base/` – the file(s) as they were before the change
- `GATEWAY-CONTRACT.md` – the contract of the notification gateway the dispatcher talks to

The working tree of this workspace already contains the candidate version.
