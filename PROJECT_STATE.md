# Project State

## Android Task 11 — Closing and Recovery

- Status: Completed.
- Initial implementation: PR #28 (`d684d72ea7fb74386219b16ce8b96fc3977b0e2b`).
- Manual Recovery hotfix: `fb7a877f223d99248c59d45972887b2344eebd44`, merged through PR #29.
- Final Android main: `ed3a5ed3b125a1d9c71210f23fca5c1ee66399cb`.
- Final backend authority: `63c86373d68cc5e4d8f2c15422f0c5bc22779df4`; latest projection fix: `43393eb97cbb588d8a2801cab1d4f5f498711135`.
- Exactly-once evidence: the real response-drop reused the same persisted lowercase UUID/body and retained exactly one Mobile POS Request and one POS Closing Entry.
- Original recovery result: Android entered `MANUAL_RECOVERY`, so the original run was BLOCKED at local recovery.
- Same-mutation salvage: explicit recovery accepted only the authoritative existing result with `replayed=true`; it created no new Closing.
- Terminal state: receipt persisted, pending recovery acknowledged and retired, local pending count `0`.
- Session state: Opening closed, Opening flow unblocked after relaunch, and no automatic replacement Opening was created.
- Android hotfix made no backend source changes.
- Verification: focused RED-to-GREEN tests and full Gradle gate PASS; P1/P2 review found none.
- Task 12: NOT STARTED.

## Next Gate

- Task 12 status: NOT STARTED.

## Opening post-login routing hotfix

- Status: Merged
- Regression: Opening post-login routing now reconciles the authoritative current session.
- Pull request: #26
- Implementation commit: `05f8dc649689dbddb15bfaba59a13c8bd90cf86b`
- Merge commit: `379ac8392cb3b40b212bba2b1686af3dd792a7de`
- Device verification: API 23 — 140 tests PASS; API 36 — 140 tests PASS.
- Process-death verification: API 36 recovery boundaries PASS.
- Independent Sol review: final findings resolved.

## Android Task 10 — History and Return

- Status: Verified and approved for delivery.
- Backend contract authority: `663c252070f10c37a1c4545f5f618189423d64f4`.
- Android scope: bounded paginated History, Sale Detail, server-authoritative remaining-returnable quantities, return quantity and reason input, server return quote, refund-mode selection, durable return mutation replay, return receipt recovery, and Compose navigation/root integration within the existing shell.
- Accounting authority: refund values, eligibility, remaining-returnable quantities, refund modes, and accounting remain server-authoritative; Android performs no local refund or accounting calculation.
- Local verification: Debug/Release unit tests PASS; Debug/Release lint PASS; Debug/Release assemble PASS; `git diff --check` PASS.
- Device verification: API 23 — 142 tests PASS; API 36 — 142 tests PASS.
- Recovery verification: return process-death harness PASS; the persisted UUID and exact serialized request bytes survived process death, replay reused the original identity/body, no new logical return was generated, the terminal response was persisted, the receipt was recovered, and logout retained the unresolved mutation.
- Return behavior verification: server-authoritative quote/refund and `RETURN_LIMIT_EXCEEDED` handling PASS.
- Final staging fault gate: `v1.sales.create_return` response-drop PASS after the completed backend response; replay used the same persisted UUID and identical request-body hash, returned HTTP 200 with the same return document reference, recovered the terminal receipt, and created exactly one return POS Invoice and exactly one Mobile POS Request with no duplicate return.
- Proxy final state: DISARMED; Caddy restored to the direct staging upstream; proxy stopped; isolated sale/returns and temporary barcode cleaned up through ERPNext-safe lifecycle operations; sanitized evidence preserved.
- Failed diagnostic attempt: an earlier operator-confirmation-timeout run did not complete the recovery gate and is not part of the PASS evidence.
- Independent review: final Sol high read-only review PASS against the unchanged final Android/tooling diff.
- Non-blocking backend lifecycle coverage gaps: a real serialized-item lifecycle has not been proven; multiple batches within one original invoice row have not been proven. Neither case is claimed as staging-verified.
