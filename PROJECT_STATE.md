# Project State

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
