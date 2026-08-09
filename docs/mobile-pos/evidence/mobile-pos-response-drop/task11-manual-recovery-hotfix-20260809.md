# Task 11 Closing manual recovery hotfix evidence

- Result: `VERIFIED`
- Date: `2026-08-09`
- Android base authority: `678133a4aa6bef0395d4f61e95a3ed5f2b787dcc`
- Backend authority: `63c86373d68cc5e4d8f2c15422f0c5bc22779df4`
- Device/API: `emulator-5554 / 25`

No token, cookie, password, authorization code, request body, or other secret is recorded here.

## Original response-drop result

The real response-drop reached the backend after Android had durably persisted the
Closing mutation. The backend committed exactly one `Mobile POS Request` and one
submitted `POS Closing Entry`, then the response was dropped. Android could not
obtain the terminal receipt through its existing recovery path and moved the
persisted mutation to `MANUAL_RECOVERY`.

The original run was therefore `BLOCKED`; it was not a direct Task 11 pass.

## Hotfix recovery result

The hotfix added an explicit Closing-only recovery action. It replayed the same
persisted lowercase UUID and exact persisted request bytes. It did not generate a
new UUID or reconstruct the request body. The backend returned the existing
terminal result with `meta.replayed=true` and the existing Closing reference
`POS-CLO-2026-00559`.

```text
uuid_sha256=90bd3242b3b45b324bfd0b3be8fe9bae525cc01022cc04d932f852c807c71c0e
persisted_encrypted_body_sha256=9c97536b1a22d61c43ccef3f099c96cbddc5303776a9964e2887aa6ac3e0fcad
uuid_unchanged=true
persisted_body_unchanged=true
backend_result_reused=true
mobile_pos_request_count=1
pos_closing_entry_count=1
duplicate_submit=false
terminal_receipt_persisted=true
pending_recovery_acknowledged=true
android_pending_mutation_count=0
opening_unblocked_after_relaunch=true
automatic_opening_created=false
```

The terminal receipt was durably persisted before publication to the UI. Normal
acknowledgement then retired the validated local mutation. The Opening flow became
available after relaunch, and Android did not create a replacement Opening.
