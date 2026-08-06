# Task 9 staging 403 and response-drop evidence

- Result: `PASS`
- Date: `2026-08-06` (`Asia/Jakarta`)
- Android commit: `44bfe5ee0ff92d103e3c365783d004d96fbee59b`
- Site: `task9-staging.localhost`
- Cashier: `task9.cashier@rotiropi.test`
- POS Profile: `Task 9 Mobile POS`
- Opening: `POS-OPE-2026-00007` (`Open`)
- Proxy: `127.0.0.1:18001` to `127.0.0.1:8000`

No OAuth token, cookie, authorization header, customer data, or request body is recorded here.

## HTTP 403 root cause and correction

The normal Android request reached `/api/method/roti_ropi_pos.api.v1.sales.submit` at
`2026-08-06T12:48:47Z` as the expected cashier, profile, and opening. The sanitized
backend envelope was:

```text
http_status=403
code=PERMISSION_DENIED
message=The operation is not permitted.
details={}
retryable=false
request_id=685aa7a0c80206ba4a1b892700
idempotency_key=f5122bac-cec1-4185-96e1-cbac6fa925e8
```

The exact rejecting path was:

```text
roti_ropi_pos.mobile_pos.invoices.submit_sale:64 -> invoice.submit()
erpnext.stock.serial_batch_bundle.SerialBatchCreation.make_serial_and_batch_bundle:1177 -> doc.save()
frappe.model.document.Document.insert:470 -> self.check_permission("create")
```

`doc` was a new `Serial and Batch Bundle`. The staging `Mobile POS Cashier` role did
not have create permission for that DocType. All earlier endpoint, role, profile,
opening, POS Invoice permission, and User Permission gates had passed.

The isolated staging correction was one `Custom DocPerm` for `Mobile POS Cashier` on
`Serial and Batch Bundle`, enabling read, write, create, and submit while leaving
cancel and delete disabled. No Android, backend application, ERPNext, Frappe, or
Docker Compose source was changed.

## Normal-sale control

After the permission correction, one proxy-disabled Android sale completed normally:

```text
uuid=907b889d-f8d0-42e6-a484-6b366ba264ef
http_status=201
invoice=ACC-PSINV-2026-00034
pos_invoice_count=1
mobile_pos_request_count=1
grand_total=200.0
paid_amount=200.0
change_amount=0.0
receipt_displayed=true
duplicate_count=0
```

The control invoice was cancelled and the exact control invoice/request records were
then deleted through Frappe document APIs.

## Deferred response-drop

The final fault run used the proxy synthetic-tested one-shot state machine. After the
backend returned the committed invoice reference, the proxy disarmed immediately and
withheld the original response beyond the Android client's read timeout. Android then
replayed the persisted mutation automatically.

```text
uuid=1ed062a9-c59a-4015-8e1d-84ea7e8dccea
original_body_sha256=9291d63011099c58331e56b98d69ef2903ee646591b23662c2c042ff758a67c9
replay_body_sha256=9291d63011099c58331e56b98d69ef2903ee646591b23662c2c042ff758a67c9
uuid_match=true
body_hash_match=true
original_upstream_status=201
replay_upstream_status=200
invoice=ACC-PSINV-2026-00034
```

Authoritative pre-cleanup evidence:

```text
pos_invoice_count=1
mobile_pos_request_count=1
invoice_transaction_uuid=1ed062a9-c59a-4015-8e1d-84ea7e8dccea
request_idempotency_key=1ed062a9-c59a-4015-8e1d-84ea7e8dccea
request_status=Completed
request_reference=ACC-PSINV-2026-00034
invoice_status=Paid
grand_total=200.0
paid_amount=200.0
change_amount=0.0
item=TASK9-BATCH-ITEM
quantity=1 Carton
payment=Cash 200.0
android_terminal_state=COMPLETED
android_dispatch_attempts=2
receipt_recovered=true
duplicate_count=0
```

The recovered Android receipt displayed `ACC-PSINV-2026-00034`, `PAID`, and `200.0`.
The sanitized proxy event stream is stored beside this file.

## Cleanup and final state

The recovered terminal result was acknowledged in the Android application. The final
invoice was cancelled, then the exact Mobile POS Request and cancelled invoice were
deleted through Frappe document APIs and committed.

```text
final_pos_invoice_count_for_uuid=0
final_mobile_pos_request_count_for_uuid=0
local_terminal_count_for_uuid=0
opening_status=Open
proxy_listener_count=0
caddy_upstream=127.0.0.1:8000
staging_ping_http_status=200
```

Final conclusion: the staging-only DocType permission defect was corrected, a normal
sale passed first, and the deferred lost-response replay produced one logical sale and
one recovered receipt without duplication.
