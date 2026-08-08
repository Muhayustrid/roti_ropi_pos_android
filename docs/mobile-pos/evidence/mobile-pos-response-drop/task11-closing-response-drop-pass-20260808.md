# Task 11 Closing response-drop evidence

- Result: `PASS`
- Date/timezone: `2026-08-08T10:07:18Z (UTC)`
- Android commit: `d684d72ea7fb74386219b16ce8b96fc3977b0e2b`
- Android base authority: `703da8418bc8873a2924428fbe9715a81203f30e`
- Backend commit: `186d2e927963a38dc408437f08dfcf10712f5e26`
- Protocol: backend `docs/mobile-pos/response-drop-closing-v1.md`
- Site label: `oauth-staging`
- POS Profile label: `Task 11 Response Drop Profile`
- Device/API: `emulator-5554 / 36`

No OAuth token, cookie, Authorization header, raw UUID, request body, cashier/customer identifier, payment account, or unrestricted amount belongs in this file.

## Preconditions

```text
dedicated_cashier=true
submitted_opening_count=1
unconsolidated_submitted_invoice_count=10
fresh_preview=true
expected_payment_mode_count=1
proxy_raw_logging_disabled=true
proxy_rule_count_before_arm=0
android_unresolved_count_before_submit=0
```

## Commit, drop, and replay

```text
uuid_original_sha256=8d53618b20c044a577dd7dd5a1e180b8750d10efb0e3106948add1d568fbe9a5
uuid_replay_sha256=8d53618b20c044a577dd7dd5a1e180b8750d10efb0e3106948add1d568fbe9a5
uuid_hash_match=true
original_body_sha256=a59f5fd8652d1a685d6292881ad26a8630d1233736fa528fdf770cee0c40895f
replay_body_sha256=a59f5fd8652d1a685d6292881ad26a8630d1233736fa528fdf770cee0c40895f
body_hash_match=true
original_upstream_status=201
replay_upstream_status=200
replay_meta_replayed=true
closing_reference=POS-CLO-2026-00512
initial_closing_status=Submitted
mobile_pos_request_count_after_commit=1
closing_entry_count_after_commit=1
backend_commit_observed_at=2026-08-08T10:07:12.551957+00:00
response_dropped_at=2026-08-08T10:07:12.552164+00:00
proxy_disarmed_at=2026-08-08T10:07:18.895963+00:00
commit_before_drop=true
proxy_rule_count_after_disarm=0
```

## Queued recovery and process death

```text
android_state_after_submit=CLOSING_QUEUED
android_submit_dispatch_count=1
process_death_harness_api23=PASS
process_death_harness_api36=PASS
same_uuid_after_restart=true
same_body_after_restart=true
submit_replayed_after_queued=false
closing_status_poll_count=1
status_poll_window_seconds=15
status_poll_bounded=true
terminal_closing_status=submitted
terminal_response_persisted_before_ui=true
receipt_recovered_after_process_death=true
logout_blocked_while_evidence_exists=true
```

## Exactly-once backend proof

```text
mobile_pos_request_operation=v1.closing.submit
mobile_pos_request_state=Completed
mobile_pos_request_count_for_uuid_hash=1
closing_entry_count_for_uuid_hash=1
request_reference_matches_closing=true
closing_transaction_correlation_matches=true
reference_matches_original_replay_status_receipt=true
duplicate_closing_count=0
```

## Authoritative terminal receipt

```text
opening_matches=true
profile_matches=true
invoice_count_matches=true
grand_total_matches=true
net_total_matches=true
total_quantity_matches=true
total_taxes_and_charges_matches=true
payment_mode_count_matches=true
payment_rows_all_match=true
reconciliation_matches=true
failure_projection_matches=true
```

## Session and bootstrap reconciliation

```text
closing_completion_refresh_count=1
sessions_current_opening_present=false
bootstrap_opening_present=false
bootstrap_open_session_capability=true
bootstrap_close_session_capability=false
automatic_sessions_open_count=0
replacement_closing_submit_count=0
```

## Cleanup

```text
android_terminal_acknowledged=true
android_local_record_count_after_ack=0
proxy_listener_count=0
proxy_upstream_restored=true
backend_records_deleted=false
manager_correction_reference=none
```

## Conclusion

`PASS` — All required observations verified.
