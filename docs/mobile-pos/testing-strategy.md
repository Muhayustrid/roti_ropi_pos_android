# Android Mobile POS Testing Strategy

## Current Baseline

Tasks 1A and 1B resolved the dependency baseline and established a verified
XML/ViewBinding shell as historical evidence. Task 2 completed the approved API
transport boundary. Task 2B then replaced only the placeholder shell with a
verified Jetpack Compose and Material 3 foundation.

On 2026-07-30, Task 2B passed 32 debug unit tests, debug/release lint and assembly,
release Kotlin compilation, Android-test APK assembly, five Compose tests on each
of API 23 and API 36, and deterministic device-state checks. Android Studio Quail
rendered five light/dark, phone/tablet, portrait/landscape, and font-scale 1.5
previews; image and semantics inspection found no clipping, unsupported feature
claim, or release fixture. AGP 9.2.1 exposes no `testReleaseUnitTest` task, so its
absence is recorded rather than reported as passing.

## Objectives

- Prove DTO compatibility with the stable and native backend response shapes.
- Prove authentication secrets and redirect handling are safe.
- Prove each logical mutation uses one UUID and one exact body.
- Prove process death and unstable networks cannot create duplicate documents.
- Prove Compose lifecycle, navigation, semantics, theme, and adaptive-layout behavior on API 23 and target API 36.
- Prove accessibility and bounded performance on low-end devices.
- Preserve executable evidence for debug and release builds.

## Test Layers

| Layer | Tool | Scope |
| --- | --- | --- |
| Pure unit | JUnit 4, coroutine test | DTO mapping, PKCE helpers, state reducers, retry policy, cart bounds |
| HTTP integration | OkHttp MockWebServer | Envelopes, native errors, timeouts, disconnects, headers, exact replay |
| Repository integration | Fakes and contract fixtures | Endpoint mapping and DTO/domain separation |
| Instrumentation | AndroidX Test and Compose UI test | Compose semantics, navigation, lifecycle, adaptive layout, Keystore, backup config |
| Device journey | Compose UI test in-app; UI Automator only across the browser boundary | Complete cashier lifecycle |
| Manual security/accessibility | APK Analyzer, TalkBack, Accessibility Scanner | Secret inspection and human-operability |
| Cross-system staging | Android app plus approved backend | OAuth, mutation replay, queued closing |

No screenshot, mocking, DI, or coverage dependency is added unless a measured
gap justifies it.

## Contract Fixtures

Keep `app/src/test/resources/api/v1/endpoint-contracts.json` as the checked-in
parameter source for exactly these 14 endpoints:

| Endpoint | Method | Input | Idempotency | Retry class |
| --- | --- | --- | --- | --- |
| `bootstrap.get` | GET | Query | No | One refresh/read retry |
| `sessions.current` | GET | Query | No | One refresh/read retry |
| `sessions.open` | POST | JSON | Required | Recovery only |
| `customers.search` | GET | Query | No | One refresh/read retry |
| `catalog.search` | GET | Query | No | One refresh/read retry |
| `catalog.scan` | POST | JSON | No | One refresh/read retry |
| `catalog.quote_item` | POST | JSON | No | One refresh/read retry |
| `sales.submit` | POST | JSON | Required | Recovery only |
| `sales.get` | GET | Query | No | One refresh/read retry |
| `sales.list` | GET | Query | No | One refresh/read retry |
| `sales.create_return` | POST | JSON | Required | Recovery only |
| `closing.preview` | GET | Query | No | One refresh/read retry |
| `closing.submit` | POST | JSON | Required | Recovery only |
| `closing.status` | GET | Query | No | One refresh/read retry |

Every row stores the exact
`/api/method/roti_ropi_pos.api.v1.<module>.<method>` path, required and optional
query/body fields, forbidden fields, required and optional response fields,
decimal-string paths, stable-error fixtures, authentication requirement,
pagination metadata, and serializer identity.

One parameterized suite asserts:

- Exactly 14 unique endpoints; no health or return-preview endpoint.
- Exact method and versioned path.
- Query encoding or JSON body placement.
- Bearer requirement.
- Idempotency on exactly four mutations.
- Automatic retry class.
- Request field names and optionality.
- Success and expected-error envelope decoding.
- DTO required/optional fields and decimal strings.
- Pagination defaults and maximums.
- Additive-field compatibility and malformed/incompatible failures.
- First and replayed mutation metadata.

Keep reviewed fixtures under `app/src/test/resources/api/v1/` for:

- Success and expected-error envelope.
- Native 401, 403, 404, 429, 500, and 503.
- Malformed response and missing or incompatible API major version.
- Bootstrap with zero, one, and multiple profiles.
- Prior-day `STALE_OPENING`.
- Customer and catalog pagination.
- Scan and UOM warning.
- Item quote.
- Sale success, replay, and `PRICE_CHANGED`.
- History and sale detail.
- Return success and `RETURN_LIMIT_EXCEEDED`.
- Closing draft, queued, submitted, failed, cancelled, Processing, Completed,
  and Rejected.
- Additive unknown fields.
- Unknown enum values.

Fixtures are copied from approved backend contract examples or captured from a
reviewed test environment without credentials or personal production data.
Every fixture records its endpoint, owning backend phase, source type, backend
version/SHA, review reference, and no-credentials/no-production-PII assertion.
Contract-example fixtures are not runtime integration evidence. A feature task
refreshes its fixtures after its backend phase is verified and reviews DTO and
fixture changes together.

## Required Unit and HTTP Tests

### Authentication

- PKCE challenge is S256 and verifier entropy is sufficient.
- No client-secret path exists.
- State and exact redirect mismatch are rejected.
- A mismatched unsolicited callback preserves the original pending attempt and
  expiry for the matching callback.
- Authorization attempt is single-use.
- Refresh is serialized.
- 401 pauses mutation replay.
- Token redaction covers headers and OAuth fields.
- `OAuthAttemptStore` covers encrypted round trip, the approved attempt lifetime
  (10 minutes only if that proposal is approved),
  origin/client/redirect binding, process death, consume-once, terminal cleanup,
  malformed data, cancellation, replay rejection, death after consumed
  persistence, and death after token persistence.
- `TokenStore` covers unique IV, tag/ciphertext tampering,
  malformed/truncated records, atomic replacement, partial-write recovery,
  origin binding, backup exclusion, logout, and key invalidation.
- API-client tests cover exact-origin Bearer attachment, disabled redirects,
  serialized refresh, one eligible-read retry, and no mutation auto-replay.
- Token exchange and refresh tests reject arbitrary absolute token endpoints,
  cross-origin endpoints, and every 30x response while accepting only the fixed
  token path on the canonical origin.
- Authorization tests accept only the fixed canonical-origin authorize path and
  scope `all`; discovery, dynamic endpoints, nested URLs, and cashier-editable
  OAuth URLs are rejected.
- Canonical-origin tests cover whitespace, every control character class,
  backslash, malformed percent escape, scheme/host case, empty host, userinfo,
  path, query, fragment, trailing-dot host, invalid/default/non-default port,
  and exact serialization without a trailing slash.

### Envelope and DTOs

- Outer Frappe `message` is required.
- Additive fields are ignored.
- Missing required fields fail as protocol errors.
- Missing or incompatible API major version stops compatibility.
- Decimal strings preserve precision.
- Native pre-dispatch responses map by HTTP status.
- `Retry-After` is preserved for 429 and 503.
- Request ID and replay metadata survive mapping.
- Unknown enum values map to an explicit unsupported state without crashing.

### State and Recovery

- UUID and body persist before transport invocation.
- Timeout sends the identical bytes and key.
- Process restart converts stale `sending` to retry.
- The body is serialized once; replay never invokes its serializer again.
- Unknown persisted body-format version enters manual recovery.
- 401 preserves body and key.
- Changed logical action receives a new key.
- `IDEMPOTENCY_KEY_REUSED` stops.
- Initial dispatch plus five retries never exceeds six network dispatches.
- `Retry-After`, stable `retry_after_seconds`, local delay, invalid values,
  attempt count, and persisted next-eligible time follow the documented policy.
- HTTP 409 `REQUEST_IN_PROGRESS` uses the same persisted delay/count and
  six-dispatch bound as other recoverable mutation responses.
- Closing polling is bounded and resumes after five minutes only from explicit
  `Check status`.
- Terminal state persists before cleanup.
- Terminal data survives a crash between persistence, rendering, and explicit
  acknowledgment.
- A different user cannot replay pending work.
- A matching username on another HTTPS origin or OAuth client cannot replay
  pending work.
- AES-GCM writes use unique IVs; tag tampering and key invalidation enter manual
  recovery.

### Feature State

- Capabilities control visible actions.
- Known-offline mutation start preserves input, creates no UUID/row/worker, and
  sends nothing.
- Connectivity restoration requires explicit Retry.
- Capability refresh occurs once after each exact documented authentication,
  profile, successful/replayed opening, successful/replayed sale,
  successful/replayed return, accepted-closing, and terminal-closing trigger;
  concurrent triggers coalesce.
- Bootstrap completion, observation, rendering, or failure cannot cause a loop.
- Failed required refresh leaves mutations disabled.
- Logout is blocked for every unresolved state and, after terminal
  acknowledgment, clears auth state, opening input, all read caches, cart,
  receipt, return/closing input and status, encrypted terminal bodies, and
  navigation state.
- Stale opening is informational.
- Customer search is debounced, paginated, and cancellable.
- Walk-in display name is hidden for registered customers.
- Catalog and history lists remain bounded.
- Cart rejects the fifty-first distinct row.
- Quote cancellation ignores stale responses.
- Batch, serial, and UOM values come from server DTOs.
- Payment and return screens cannot run while their backend contract gates are
  unresolved.
- Overpayment input and local change calculation are absent.
- Closing transport-unknown and confirmed Processing states replay the exact
  submit with the same key, a bare Draft enters manual recovery, Completed
  queued state polls, and failed or cancelled state directs the cashier to
  manager review without replacement.

The closing assertion above uses request disposition, not Draft alone:
Processing may replay the stored submit, Completed uses the stored result,
Rejected is terminal, and a bare Draft enters manual recovery.

## Instrumentation Matrix

### Deterministic Devices

Required SDK packages are:

```text
platform-tools
emulator
the exact compile platform approved by Task 1A
system-images;android-23;google_apis;arm64-v8a on arm64 hosts
system-images;android-23;google_apis;x86_64 on x86_64 hosts
system-images;android-36;google_apis;arm64-v8a on arm64 hosts
system-images;android-36;google_apis;x86_64 on x86_64 hosts
```

`create-test-avds.sh` reads the actual Gradle compile SDK configuration instead
of duplicating its value. It fails unless that platform and the exact API 23 and
API 36 system images are installed. Compile SDK may differ from the two required
device APIs.

The scripts fail if the matching host image, hardware acceleration, or target
API is unavailable:

```bash
./tools/create-test-avds.sh
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

The fixed AVD names are `mobile-pos-api23` and `mobile-pos-api36`. The API 23
profile uses 2 CPUs, 1 GiB RAM, 720 x 1280, and 320 dpi. The API 36 profile uses
4 CPUs, 2 GiB RAM, 1080 x 1920, and 420 dpi. Both use a wiped data image, no
snapshot, `en-US`, UTC, disabled system animations, a bounded boot timeout, and
readiness checks for boot completion and Package Manager. The scripts record
the exact system-image revision, emulator version, and hardware configuration.
The runner:

- Starts exactly one requested AVD.
- Resolves its serial and verifies its AVD name and API.
- Uses `adb -s <serial>` for every operation.
- Builds APKs, installs them through that serial, and invokes instrumentation
  directly.
- Excludes every test annotated `SpecialHarnessOnly`; host scripts select those
  tests by exact class and method.
- Stops only the emulator it started.
- Writes device properties, commands, exit codes, instrumentation output, and
  JUnit XML under `app/build/reports/mobile-pos-devices/<api>/`.

A bare `connectedDebugAndroidTest` with an ambient device set is not API-matrix
evidence.

Run connected tests on:

- API 23 device or emulator.
- API 36 device or emulator.
- Portrait and landscape.
- Font scale 1.0 and 1.5.
- TalkBack-enabled core journey.
- "Don't keep activities" enabled for lifecycle checks.

Final matrix commands are:

```bash
./tools/run-device-tests.sh api23 --matrix
./tools/run-device-tests.sh api36 --matrix
./tools/accessibility-harness.sh api23
./tools/accessibility-harness.sh api36
```

The matrix records and resets orientation, font scale, animation scales, and
"Don't keep activities" for each run. Missing required API or state evidence
blocks the task; ambient-device or best-effort evidence is not accepted.

Required instrumentation scenarios include:

- Sign-in screen and browser launch.
- Cold and warm OAuth completion through one AppAuth redirect receiver.
- Rejected malformed redirect.
- Merged-manifest verification for one exported receiver and one non-exported
  completion activity.
- Forged explicit Intent, duplicate parameters, and failed App Link verification.
- App Link re-verification polls boundedly to a terminal state and asserts only
  the exact expected host.
- Keystore token round trip and deletion.
- Compose content follows Activity lifecycle, preserves only approved saveable UI state, and retains no Activity or obsolete composition reference after recreation.
- Back navigation does not duplicate submission.
- Recovery before a new mutation.
- Full opening-to-closing navigation using a fake or staging API.
- Accessibility labels, focus order, touch targets, and error announcements.

## Unstable-Network Tests

MockWebServer tests simulate client transport behavior:

- Disconnect before request body.
- Disconnect after request body.
- Accepted request followed by response-body loss.
- Delayed response and client timeout.
- HTTP 429/503 with `Retry-After`.
- HTTP 401 followed by refresh success and failure.
- HTTP 500 retry exhaustion.
- Queued closing with delayed terminal state.

For each mutation scenario, assert the exact network dispatch count, UUID,
request bytes, attempt count, and next-eligible time. Disconnect-before-body,
disconnect-after-body, response-body loss, timeout, 429/503, 500, 401, and
process restart must not trigger an API-client mutation retry outside
`RecoveryCoordinator`.

MockWebServer cannot prove a real server commit. Lost-response evidence is an
external staging/backend gate. An approved operator owns the one-shot response
drop behind the normal HTTPS ingress and supplies an evidence ID. Android must
show the same transaction ID, original reference, and exactly one ERPNext
document after replay for each of `sessions.open`, `sales.submit`,
`sales.create_return`, and `closing.submit`.

Each external evidence record contains the operation, environment, backend SHA,
original and replay UUID, confirmation that the drop occurred after upstream
completion, original business reference, resulting ERPNext document type/name,
document count of exactly one, operator evidence ID, and approved protocol
reference. Task 12 validates the contents and correlation, not merely the
presence of four IDs.

No proxy implementation, certificate, credential, control endpoint, or
deployment automation is owned by Android.

Queued-closing forcing is also externally owned. The staging procedure must use
active workers and an approved deterministic trigger, expose no Android
endpoint, add no production test hook, and require no Android control of ingress
or workers.

## Process-Death Harness

OAuth and mutation recovery use separate host scripts rather than a single
instrumentation process:

```bash
./tools/oauth-process-death.sh api23
./tools/recovery-process-death.sh api23
```

Each script starts its fixed AVD, verifies its name and API, builds APKs without
an ambient connected-device install task, and uses `adb -s <serial>` for every
install, force-stop, relaunch, and instrumentation operation. The mutation
script runs one `adb shell am instrument` method to persist a prepared or
`sending` mutation, exits that runner, force-stops the application, relaunches
it, then runs a second instrumentation method to verify the same site, user,
UUID, and byte-equivalent body were recovered. `ActivityScenario.recreate()` is
used only for configuration-change coverage and is not process-death evidence.

Every setup/verification method controlled by a host script is annotated
`SpecialHarnessOnly`. Broad device runs exclude that annotation. Host scripts
select one exact class and method per invocation and write artifacts under
`app/build/reports/mobile-pos-process-death/<harness>/<serial>/<timestamp>/`.

The OAuth script similarly persists an active attempt, force-stops, delivers a
callback after restart, uses a test-only exchanger, verifies one successful
exchange, and verifies terminal attempt deletion. Injected modes also kill the
process after consumed-attempt persistence and after token persistence. Restart
must never exchange the consumed code again; it either requires a new
authorization or retains matching persisted tokens and deletes the stale
attempt.

## Low-Memory and Performance Tests

Run:

```bash
./tools/performance-harness.sh api23
./tools/performance-harness.sh --serial <approved-physical-device-serial>
```

The harness uses:

- The fixed API 23 low-end AVD.
- A separately approved representative low-end physical device for final
  acceptance. The AVD result is regression evidence only.
- A test-only application, runner, manifest, TLS fixture, and injected HTTPS
  MockWebServer. They exist only under `androidTest`; production trust and the
  production APK are unchanged.
- Seed `20260724`.
- Fixed timestamps and request IDs.
- 100 customers, catalog rows, and history rows.
- A 50-row cart and 50 sale/receipt cycles.
- Five unrecorded warm-ups and 30 recorded samples.

It records:

- Cold-launch time through `am start -W`.
- Request latency p50/p95 for `bootstrap.get`, `catalog.search`, and
  `sales.submit`.
- Critical UI action-to-Compose-idle p50/p95.
- Total PSS before and after 50 cycles.
- StrictMode failures and retained destroyed views.

A cold launch force-stops the package, drops the app process, waits for device
idle, then measures `am start -W` `TotalTime` until the sign-in or restored
bootstrap destination is displayed. Request latency begins immediately before
OkHttp dispatch and ends after the complete response is parsed. UI latency
begins at the injected input event and ends after Compose reaches idle with the
expected state rendered. PSS uses `dumpsys meminfo` after GC/idle at the same checkpoint
before warm-ups and after the fiftieth cycle.

Artifacts are written under:

`app/build/reports/mobile-pos-performance/<serial>/<timestamp>/`

Required artifacts are metadata, Git SHA, seed, device configuration, commands,
raw CSV samples, summary JSON, instrumentation output, logcat, and before/after
meminfo.

All performance and accessibility fixtures use synthetic data. Reports, logcat,
screenshots, and scanner traces must contain no token, OAuth redirect,
authorization code, verifier, mutation body, or production customer data.

Current proposed thresholds are:

- Critical UI-flow p95 at most 250 ms.
- Total PSS growth below 20 MiB.
- No retained Activity or obsolete composition reference.
- No main-thread disk/network StrictMode violation.
- No out-of-memory failure.

No proposed numeric threshold is a pass gate until explicitly approved. Launch
and request p50/p95, UI-flow p95, and PSS growth are recorded as
`NOT_EVALUATED` while their thresholds remain unapproved. Final completion is
blocked until numeric launch-p95, per-request-p95, UI-flow-p95, and PSS-growth
thresholds are approved. A missing threshold is never reported as PASS.

## Compose UI and Preview Verification

The Compose foundation and every major screen include tests or debug previews for:

- Loading, empty, populated when integrated, offline, unavailable, and error states.
- Light and dark themes with every supported accent.
- Phone and tablet widths in portrait and landscape.
- Font scale 1.0 and 1.5.
- Safe-area and navigation-bar insets.
- Stable list/grid keys, tab back-stack preservation, and no duplicate destination.
- Cashier phone cart sheet and expanded-width persistent cart pane.
- Semantics labels, roles, state descriptions, keyboard focus, and 48 dp targets.

Populated synthetic fixtures live only in `app/src/debug/` or test source sets and
are visibly labeled `Demo data`. Release builds fail verification if preview
fixtures or mock ERPNext records are packaged. Dashboard and Reports release
states stay unavailable or explicitly partial until an approved contract supplies
complete aggregates; a bounded `sales.list` page is never tested or labeled as a
complete daily report.

Variant source sets, not a runtime flag, enforce that boundary for the shell.
`com.rotiropi.pos_erpnext.ui.demo.PosDemoStates` is declared twice: populated with
`supported = true` in `app/src/debug/`, and as an unavailable stub with
`supported = false` in `app/src/release/`. `PosShell` reads it, so the debug
`Demo layout` toggle under More renders only when `supported` is true, defaults
off, and release packages no fixture. `ReleaseFixtureExclusionTest` asserts no
`app/src/main` or `app/src/release` Kotlin file declares `demoData = true`.

Preview rendering uses the installed Android Studio or Android CLI command after
its current help is inspected. Preview rendering is visual evidence, not a
replacement for API 23/API 36 instrumentation or accessibility journeys.

## Accessibility Verification

Run `tools/accessibility-harness.sh` on API 23 and API 36. It records package,
device/API, orientation, font scale, animation state, TalkBack state, commands,
exit codes, instrumentation output, and a generated checklist under
`app/build/reports/mobile-pos-accessibility/<api>/<serial>/<timestamp>/`.

The checklist requires PASS/FAIL and evidence for every core screen:

- Compose semantics and accessibility checks where stable.
- Visible label or content description and minimum 48 dp touch target.
- Logical focus order and announced loading, recoverable, and terminal errors.
- No status conveyed by color alone.
- TalkBack journey through sign in, opening, item add, payment, receipt, return,
  closing, and recovery.
- External keyboard and scanner operation.
- Font scale 1.5 in portrait and landscape without clipped primary actions or
  hidden totals.

Any unchecked or failed required item blocks completion. Synthetic data and the
artifact redaction rules above are mandatory.

## Standard Commands

Run from the Android repository root:

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
```

When an API 23 or API 36 device/emulator is available:

```bash
./tools/create-test-avds.sh
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Preserve separate API 23 and API 36 artifacts. An unspecified attached-device
run is not release evidence.

The current release build has optimization disabled. Passing
`assembleRelease` verifies the release configuration but does not establish R8
optimization or production signing.

## Release Security Verification

Before a release claim:

- Inspect the merged release manifest.
- Inspect generated resources and BuildConfig values.
- Inspect the release APK with APK Analyzer or `apkanalyzer`.
- Search source, generated configuration, and unpacked APK strings for forbidden
  resource keys and an externally supplied denylist of known development/staging
  credential values. Generic class names such as `AuthorizationCode` or
  `verifier` are not credential evidence.
- Verify cleartext traffic is disabled.
- Verify backup and device-transfer exclusion.
- Verify no signing keystore or password is stored in the repository.
- Verify release logs redact credentials and request bodies.
- Record the Git SHA, backend SHA/version, commands, exit codes, device/API
  matrix, and staging request IDs.

Run:

```bash
MOBILE_POS_RELEASE_DENYLIST=/absolute/external/path/denylist.txt \
  ./tools/verify-release-artifacts.sh app/build/outputs/apk/release/app-release.apk
```

The script fails if the APK or denylist is absent, unreadable, or empty. The
denylist stays outside the repository, is not copied to reports, and its values
are never printed. The script selects the exact APK argument, records its
SHA-256, prints the merged-manifest and generated-configuration input paths,
uses `apkanalyzer` plus an isolated APK extraction directory, searches source,
generated values, unpacked strings, repository paths, and captured release logs,
and writes only redacted findings, commands, and exit codes under
`app/build/reports/mobile-pos-release/<apk-sha256>/`.

## Phase Gates

| Android phase | Required evidence |
| --- | --- |
| Platform/API foundation | Unit, lint, debug build, release build, envelope fixtures |
| Authentication/bootstrap | Redirect, PKCE, Keystore, route, API 23/36 tests; backend Phase 3 |
| Recovery/opening | Process-death, replay, and exactly-one Opening Entry staging evidence; backend Phases 2 and 4 |
| Customer/catalog/cart | Pagination, cancellation, scan/UOM tests; backend Phases 4 and 5 |
| Sale/payment | Full-settlement and lost-response staging; backend Phase 6 and resolved payable contract |
| History/return | Scope, return-limit, and exactly-one return POS Invoice staging evidence; backend Phase 6 and resolved refund contract |
| Closing | Sync, queued, failed, bounded polling, and exactly-one Closing Entry staging evidence; backend Phase 7 |
| Final | All commands, four correlated mutation evidence records, accessibility, physical-device performance, security, and full staging lifecycle |

## Stop Conditions

- Any required command fails.
- The compile SDK and dependency versions are incompatible.
- API 23 or API 36 behavior is unverified.
- A fixture differs from the approved contract.
- A secret or cleartext path is detected.
- Duplicate business documents appear.
- Low-memory, lifecycle, or accessibility checks fail.
- Backend staging or phase evidence is unavailable.
- Release or product completion is claimed from source inspection alone.
