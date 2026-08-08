# Task 11 Closing response-drop evidence

- Result: `NOT RUN`
- Date/timezone: `<observed>`
- Android commit: `<sha>`
- Android base authority: `703da8418bc8873a2924428fbe9715a81203f30e`
- Backend commit: `186d2e927963a38dc408437f08dfcf10712f5e26`
- Protocol: backend `docs/mobile-pos/response-drop-closing-v1.md`
- Site label: `<sanitized>`
- POS Profile label: `<sanitized>`
- Device/API: `<serial-or-label> / <23|36>`

No OAuth token, cookie, Authorization header, raw UUID, request body, cashier/customer identifier, payment account, or unrestricted amount belongs in this file.

## Preconditions

```text
dedicated_cashier=true|false
submitted_opening_count=<count>
unconsolidated_submitted_invoice_count=<count>
fresh_preview=true|false
expected_payment_mode_count=<count>
proxy_raw_logging_disabled=true|false
proxy_rule_count_before_arm=<count>
android_unresolved_count_before_submit=<count>
```

## Commit, drop, and replay

```text
uuid_original_sha256=<sha256>
uuid_replay_sha256=<sha256>
uuid_hash_match=true|false
original_body_sha256=<sha256>
replay_body_sha256=<sha256>
body_hash_match=true|false
original_upstream_status=<status>
replay_upstream_status=<status>
replay_meta_replayed=true|false
closing_reference=<sanitized-reference>
initial_closing_status=<status>
mobile_pos_request_count_after_commit=<count>
closing_entry_count_after_commit=<count>
backend_commit_observed_at=<timestamp>
response_dropped_at=<timestamp>
proxy_disarmed_at=<timestamp>
commit_before_drop=true|false
proxy_rule_count_after_disarm=<count>
```

## Queued recovery and process death

```text
android_state_after_submit=CLOSING_QUEUED|<other>
android_submit_dispatch_count=<count>
process_death_harness_api23=PASS|FAIL|NOT_RUN
process_death_harness_api36=PASS|FAIL|NOT_RUN
same_uuid_after_restart=true|false
same_body_after_restart=true|false
submit_replayed_after_queued=false|true
closing_status_poll_count=<count>
status_poll_window_seconds=<seconds>
status_poll_bounded=true|false
terminal_closing_status=submitted|failed|cancelled|<other>
terminal_response_persisted_before_ui=true|false
receipt_recovered_after_process_death=true|false
logout_blocked_while_evidence_exists=true|false
```

## Exactly-once backend proof

```text
mobile_pos_request_operation=v1.closing.submit
mobile_pos_request_state=Completed|Rejected|Processing|<other>
mobile_pos_request_count_for_uuid_hash=<count>
closing_entry_count_for_uuid_hash=<count>
request_reference_matches_closing=true|false
closing_transaction_correlation_matches=true|false
reference_matches_original_replay_status_receipt=true|false
duplicate_closing_count=<count>
```

## Authoritative terminal receipt

Do not copy unrestricted amounts. Compare live values and record booleans/counts only.

```text
opening_matches=true|false
profile_matches=true|false
invoice_count_matches=true|false
grand_total_matches=true|false
net_total_matches=true|false
total_quantity_matches=true|false
total_taxes_and_charges_matches=true|false
payment_mode_count_matches=true|false
payment_rows_all_match=true|false
reconciliation_matches=true|false
failure_projection_matches=true|false
```

## Session and bootstrap reconciliation

```text
closing_completion_refresh_count=<count>
sessions_current_opening_present=true|false
bootstrap_opening_present=true|false
bootstrap_open_session_capability=true|false
bootstrap_close_session_capability=true|false
automatic_sessions_open_count=<count>
replacement_closing_submit_count=<count>
```

## Cleanup

```text
android_terminal_acknowledged=true|false
android_local_record_count_after_ack=<count>
proxy_listener_count=<count>
proxy_upstream_restored=true|false
backend_records_deleted=false|true
manager_correction_reference=<none-or-sanitized-reference>
```

## Conclusion

`NOT RUN` — replace only after every required observation exists. Any missing commit-before-drop, exact replay, exactly-once, real queued transition, durable receipt, bounded polling, session reconciliation, or proxy-disarm proof makes result `FAIL`.
