# Task 11 Closing response-drop runbook

## Authority and scope

- Android authority starts at `703da8418bc8873a2924428fbe9715a81203f30e`.
- Backend authority is fixed at `186d2e927963a38dc408437f08dfcf10712f5e26`.
- Protocol authority is backend `docs/mobile-pos/response-drop-closing-v1.md` at that SHA.
- This is one staging-only Closing run. Do not change Android, backend, Frappe, ERPNext, Bakery, proxy, or site source during evidence capture.
- Use normal OAuth + PKCE ingress and one dedicated cashier/profile/opening.
- Do not record tokens, cookies, Authorization headers, raw UUIDs, request bodies, cashier/customer identifiers, payment accounts, or unrestricted amounts.

## Preconditions

1. Confirm Android commit and fixed backend SHA.
2. Confirm dedicated staging cashier has only approved Mobile POS access.
3. Confirm selected POS Profile has one submitted Open POS Opening Entry.
4. Create at least ten submitted unconsolidated POS Invoices so worker processing exposes a real `queued` transition.
5. Fetch fresh `closing.preview` and retain server `preview_id`, exact payment-mode set, and counted policy only in live test state.
6. Enter every counted value using accepted decimal syntax. Do not calculate or alter authoritative totals.
7. Confirm external proxy raw header/body logging is disabled.
8. Confirm no existing Task 11 proxy rule is armed and no unresolved Android mutation exists.

Any failed precondition stops run. Do not manufacture substitute evidence.

## Arm and submit

1. Start sanitized proxy event capture.
2. Arm one-shot rule for exactly one `POST /api/method/roti_ropi_pos.api.v1.closing.submit` response.
3. Submit Closing once from Android.
4. Proxy forwards complete request and waits for upstream completion.
5. From a fresh backend DB connection, verify:
   - one `Mobile POS Request` for operation `v1.closing.submit`;
   - request state `Completed`;
   - one persisted `POS Closing Entry` with same transaction correlation;
   - request reference equals Closing name.
6. Record sanitized Closing reference, original HTTP status, initial Closing status, `backend_commit_observed_at`, and hashes only.
7. Drop downstream response after commit proof.
8. Immediately disarm rule. Record `response_dropped_at`, `proxy_disarmed_at`, and listener/rule count.

Failure to prove commit before drop or immediate disarm is run FAIL.

## Replay and recovery

1. Let Android timeout and use existing durable recovery path.
2. Verify replay carries same idempotency UUID and exact persisted request bytes using hashes only:
   - `uuid_hash_match=true`;
   - `body_hash_match=true`.
3. Require replay HTTP 200, `meta.replayed=true`, and same Closing reference.
4. Verify backend still has exactly one request and one Closing Entry.
5. After queued response becomes durable, restart Android process with:

   ```bash
   ./tools/recovery-process-death.sh <api23|api36> closing
   ```

   Host harness proves queued startup preserves UUID/body, does not replay `closing.submit`, persists exact terminal status envelope, restores authoritative receipt, and keeps logout blocked until acknowledgement.
6. In real staging flow, Android polls only `GET /api/method/roti_ropi_pos.api.v1.closing.status?name=<reference>` with bounded foreground delays.
7. Capture real `queued` to `submitted` or `failed` transition.
8. Confirm terminal response becomes durable before terminal UI.

## Terminal checks

For `submitted`:

- Receipt reference, Opening, profile, invoice count, grand/net/tax/quantity totals, and every payment opening/expected/counted/difference value match backend persisted values.
- Android requests authoritative bootstrap refresh once.
- `sessions.current` has no active Opening.
- Bootstrap may enable `open_session`.
- Android does not call `sessions.open` automatically.

For `failed` or `cancelled`:

- Receipt exposes only stable failure/status data.
- Android does not run successful-close refresh callback.
- Android does not create or submit replacement Closing.
- Manager workflow remains authoritative.

## Required evidence fields

Copy `task11-closing-response-drop-evidence-template.md` to a dated evidence file. Fill only observed values. Keep proxy JSONL sanitized beside it.

Required proof:

- Android commit and fixed backend SHA.
- Sanitized site/profile labels.
- Original/replay UUID hash match and body SHA-256 match.
- Original HTTP status, replay HTTP status, `meta.replayed`.
- Commit timestamp before drop timestamp; disarm timestamp after drop.
- One request row and one Closing Entry before and after replay.
- Same Closing reference across original, replay, status, and receipt.
- Real queued and terminal observations.
- Durable Android states `CLOSING_QUEUED` then `COMPLETED`.
- Submit dispatch count stops after queued discovery.
- Status polling bounded.
- Terminal receipt recovered after process death.
- Logout blocked while evidence exists.
- Final bootstrap/session projection and zero automatic Opening submissions.

## Cleanup

- Acknowledge terminal Android evidence only after receipt review.
- Do not delete or directly edit submitted Closing, Opening, invoices, accounting rows, or Mobile POS Request.
- Keep backend request under normal retention.
- Any business correction uses ERPNext manager workflow.
- Stop proxy capture, verify zero armed listeners, and restore normal upstream routing.
- Record cleanup result. A failed cleanup gate keeps Task 11 incomplete.
