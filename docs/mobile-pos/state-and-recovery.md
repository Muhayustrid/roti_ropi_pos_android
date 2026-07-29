# Android Mobile POS State and Recovery

## Authority

Server idempotency, replay, retention, and closing recovery semantics are defined
by backend
[`idempotency-and-recovery.md`](backend/idempotency-and-recovery.md). This
document defines Android persistence, retry orchestration, and UI recovery state.

## Goals

- Prevent duplicate business mutations.
- Recover unknown results after timeout, process death, or restart.
- Preserve pending work across reauthentication.
- Avoid an offline ledger or any assumption that ERPNext accepted a request.

## State Ownership

| State | Owner | Persistence |
| --- | --- | --- |
| Tokens | `TokenStore` | Keystore-encrypted atomic file |
| Active OAuth attempt | `OAuthAttemptStore` | Keystore-encrypted atomic file |
| Bootstrap session and capabilities | `MobilePosRepository` | Memory only |
| Screen rendering and search | Feature ViewModel | Memory |
| Cart draft | Sale ViewModel | Configuration change only |
| Prepared or unknown mutation | `PendingMutationStore` | Keystore-encrypted SQLite row |
| Terminal receipt currently displayed | Feature ViewModel after durable terminal write | Memory |
| ERPNext accounting state | Server | Never duplicated locally |

Unsubmitted cart state is not a transaction and is not promised across process
death. A mutation becomes recoverable only after its exact body and UUID have
been durably stored.

`MobilePosRepository` is the sole capability owner. A process starts with all
mutation capabilities disabled. Feature ViewModels never persist or infer
capability values.

## Pending Mutation Record

Each record contains:

- Lowercase UUID transaction ID.
- Authenticated cashier identity.
- Normalized HTTPS origin and configured OAuth client identity.
- Stable endpoint and HTTP method.
- Versioned encrypted blob containing the exact serialized request body.
- Exact content type, serializer identity, and local body-format version.
- Creation time.
- Local recovery state.
- Attempt count and next eligible attempt time.
- Non-sensitive request ID or business reference when known.
- Encrypted terminal response until UI acknowledgment.

No token is stored in the mutation table.

Each encrypted blob stores its format version, a fresh random 96-bit GCM IV,
ciphertext, and authentication tag. An IV is never reused with the same key.
Authenticated-decryption failure or permanent key invalidation enters
`manual_recovery`; it never falls back to plaintext or a replacement request.
The record and state transition are committed atomically in one SQLite
transaction.

The body is produced once from the endpoint request DTO using the shared JSON
serializer as UTF-8 with no pretty printing. Android persists and reuses those
exact bytes; it never reconstructs a retry body from domain or UI state. Server
validation and semantic canonicalization remain authoritative. An unknown local
body-format version enters `manual_recovery`.

## Local States

| State | Meaning |
| --- | --- |
| `prepared` | Persisted and never sent |
| `sending` | A request is in flight; process death converts it to unknown |
| `waiting_retry` | Result is unknown or retryable |
| `auth_required` | HTTP 401 paused transmission |
| `request_in_progress` | `closing.submit` reported server request Processing |
| `closing_queued` | Submit is Completed but Closing Entry status remains queued |
| `completed` | Terminal success was persisted |
| `rejected` | Terminal stable business error, including durable closing Rejected, was persisted |
| `manual_recovery` | Automatic retry bound was reached or local invariant failed |

Only one unresolved logical mutation is active per authenticated cashier. This
prevents a cashier from starting dependent work while an earlier result is
unknown.

## Prepare-Before-Send Algorithm

1. Validate the user action locally without making business decisions.
2. If connectivity is known unavailable, return `NotStartedOffline` without UUID
   allocation, serialization, persistence, worker scheduling, or transport.
3. Generate `UUID.randomUUID().toString()`.
4. Serialize the normalized request exactly once.
5. Encrypt the exact bytes with a fresh random IV and insert the site-bound
   record, content type, serializer identity, and format version as `prepared`.
6. Commit the local transaction.
7. Mark it `sending`.
8. Send the stored bytes with `X-Idempotency-Key`.
9. Persist the response and terminal or recoverable state.
10. Only then notify UI.
11. Render or reconcile from the persisted terminal result without deleting it.
12. Delete terminal data only after explicit UI acknowledgment is durably
    recorded.

A retry reads and sends the stored bytes. It never reconstructs the body from
current UI state.

## Failure Policy

| Failure | Action | UUID/body |
| --- | --- | --- |
| Connection loss or timeout | Retry after bounded backoff | Reuse |
| HTTP 429/503 | Honor `Retry-After`, then retry | Reuse |
| HTTP 500 | Limited retry, then manual recovery | Reuse |
| HTTP 401 | Persist `auth_required`, recover same identity, resolve through `RecoveryCoordinator` | Reuse |
| Unknown `closing.submit` transport result | Preserve `waiting_retry` and replay stored submit after backoff | Reuse |
| `REQUEST_IN_PROGRESS` from `closing.submit` | Preserve confirmed Processing and replay stored submit after server delay | Reuse |
| Successful or replayed `closing.submit` | Persist Completed stored result | Preserve |
| Stable closing submit error | Persist Rejected and require explicit corrected action | New key for new action |
| Completed submit with queued closing | Poll `closing.status` | Preserve reference |
| Bare Draft without submit disposition | Protocol ambiguity; manual recovery | Preserve |
| Cancelled closing | Persist terminal manager-controlled state | Retire after acknowledgment |
| `IDEMPOTENCY_KEY_REUSED` | Stop as local corruption/conflict | Never resend |
| HTTP 400/403/404/422 stable error | Persist terminal rejection and show correction | New key only for a new action |
| Success or replayed success | Persist terminal result | Retire after acknowledgment |
| Protocol ambiguity | Manual recovery; never assume success | Preserve |

The initial dispatch is not a retry. At most five automatic retries follow it,
for a maximum of six network dispatches. Before every dispatch, increment and
persist `attempt_count`; before scheduling a retry, persist `next_eligible_at`.
Both values survive process restart.

Local retry delays are 1, 2, 4, 8, and 16 seconds plus 0-500 ms jitter. For HTTP
429 or 503, a valid delta-seconds or HTTP-date `Retry-After` is considered. For
any stable response carrying `retry_after_seconds`, including HTTP 409
`REQUEST_IN_PROGRESS`, that server delay is considered. The latest of the valid
server delay and local delay becomes persisted `next_eligible_at`. Invalid,
negative, or past values are ignored. Every dispatch increments the same
persisted count, and the six-dispatch maximum also applies to confirmed
Processing replay. After the fifth retry, the record enters `manual_recovery`
and remains visible.

### Closing Request and Document States

`Processing`, `Completed`, and `Rejected` describe the backend idempotency
request. `draft`, `queued`, `submitted`, `failed`, and `cancelled` describe the
Closing Entry.

- Processing may be resumed only through replay of the exact stored submit after
  the contracted delay.
- Completed returns the stored result. If that result is queued, later state is
  obtained from `closing.status`.
- Rejected is terminal and is never automatically submitted again.
- Draft alone cannot distinguish Processing from Rejected and therefore never
  authorizes replay.

## WorkManager

Use one unique work item per transaction ID with a network constraint.

WorkManager:

- Reads the persisted record.
- Confirms the authenticated user matches.
- Stops on 401.
- Reuses the stored body and UUID.
- Applies bounded attempts.
- Uses persisted `attempt_count` and `next_eligible_at`; it has no independent
  retry counter or backoff policy.
- Persists every transition.
- Never starts a new logical mutation.
- Never serves immediate UI requests.

When automatic attempts are exhausted, recovery becomes user-visible rather
than polling indefinitely.

Network restoration never creates or sends work rejected before preparation.
It may run one unique worker only for an already persisted eligible mutation.
HTTP 401 cancels or stops that worker until the same identity reauthenticates.

## Process Death and Restart

At startup:

1. Load encrypted credentials.
2. Authenticate or reauthenticate.
3. Load unresolved mutation records.
4. Refuse replay under a different user, canonical origin, or OAuth client.
5. Convert stale `sending` state to `waiting_retry`.
6. Resume only through the operation's approved idempotency/status workflow.
7. Refresh bootstrap after terminal reconciliation.
8. Continue normal navigation only when dependent state is safe.

Every `sending` row loaded from a prior process is stale because no request from
that process can still be observed locally. Conversion preserves the UUID,
exact bytes, `attempt_count`, and `next_eligible_at`.

Process-death evidence uses two separate host-driven instrumentation invocations:
one prepares durable state and exits, ADB force-stops and relaunches the app,
and a second invocation verifies recovery. An externally owned staging/backend
fault gate may drop a response after upstream completion. Android consumes that
condition but does not own or deploy the gate.

Cross-system evidence is required separately for `sessions.open`,
`sales.submit`, `sales.create_return`, and `closing.submit`. Each record binds
the operation, environment, backend SHA, original/replay UUID, confirmed drop
after upstream completion, original business reference, exactly one resulting
ERPNext business document, operator evidence ID, and approved protocol
reference.

## Opening Recovery

- Replay `sessions.open` with the original UUID and body.
- `sessions.current` may reconcile an opening after a lost response.
- Never create a second opening merely because local receipt state is absent.

## Sale Recovery

- Replay `sales.submit`.
- Do not infer success from history search.
- Accept the replayed server receipt as authoritative.
- Block another sale while the result is unknown.

## Return Recovery

- Replay `sales.create_return`.
- Never alter source quantities locally to force a result.
- A corrected return after terminal rejection is a new logical action and key.

## Closing Recovery

- Unknown submit results remain local `waiting_retry` and may replay the exact
  submit after backoff.
- `REQUEST_IN_PROGRESS` records confirmed Processing and may replay the exact
  submit after the contracted delay.
- Completed persists the stored submit result.
- Rejected displays the durable error and never auto-resubmits.
- Draft without request disposition enters manual recovery.
- Poll `closing.status` only while a Completed submit result is `queued`.
- Foreground polling uses 2, 4, 8, 16, then 30-second delays and stops after
  five minutes.
- No automatic status worker continues after five minutes. The durable queued
  state remains visible; an explicit `Check status` action starts another
  bounded foreground cycle.
- A failed closing is terminal for Android and directs the cashier to a manager.
- A cancelled closing is terminal and manager-controlled; refresh bootstrap and
  do not create a replacement automatically.
- Android never creates a replacement Closing Entry.

## Authentication and Logout

HTTP 401 does not mean logout. It pauses the pending request and starts
credential recovery.

Normal logout is blocked for `prepared`, `sending`, `waiting_retry`,
`auth_required`, `request_in_progress`, `closing_queued`, and
`manual_recovery`. A `completed` or `rejected` result must first be explicitly
acknowledged. Logout then removes tokens, active OAuth attempts, opening input,
customer/catalog/history caches, cart, receipt, return and closing input/status,
navigation state, in-memory responses, and encrypted terminal bodies.

If Keystore data becomes unreadable while a transaction is unresolved, the app
enters manual recovery and displays non-sensitive support identifiers. It does
not erase the record or generate a replacement key.

## No Offline Sale

Known-offline new mutations preserve UI input and create nothing. Connectivity
restoration requires explicit Retry. WorkManager may resume only a mutation
already durably prepared before connectivity was lost.

The app may prepare a mutation while online interaction is active and may retain
it after connectivity loss. It must not:

- Allow a cashier to continue creating sales as an offline queue.
- Mark a sale paid locally.
- Assign invoice names or posting state.
- Maintain stock or accounting balances.
- Promise that a pending request reached ERPNext.

## Acceptance Criteria

- Persistence completes before the first network byte is sent.
- Timeout and process-death retries use byte-equivalent bodies and the same UUID.
- A lost opening, sale, return, or closing response creates exactly one
  corresponding ERPNext business document.
- HTTP 401 preserves state and resumes only as the same cashier.
- A different user cannot inspect or replay pending data.
- Queued closing polling is bounded.
- Terminal response persistence precedes record retirement.
- Terminal data survives process death until explicit UI acknowledgment; a new
  logical action receives a new UUID, and `IDEMPOTENCY_KEY_REUSED` never resends.
- Offline-start creates no transaction record or UUID.
- Closing Draft state alone never triggers replay.
- No offline ledger or unbounded retry loop exists.
