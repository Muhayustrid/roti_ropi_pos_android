# Android Mobile POS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved Android cashier client for the versioned Mobile
POS API without duplicating ERPNext business logic.

**Architecture:** One Kotlin Android application module uses Jetpack Compose,
Material 3, immutable ViewModel state, a small repository/API boundary, AppAuth,
and encrypted OAuth-attempt, token, and durable mutation storage. ERPNext remains
authoritative, every mutation is prepared before transmission, and known-offline
startup never exposes an offline transaction workflow. Task 1B's verified
XML/ViewBinding shell remains historical evidence until Task 2B replaces it.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Jetpack Compose, Material 3, AndroidX,
coroutines, OkHttp, Kotlin serialization, AppAuth, WorkManager, JUnit 4,
MockWebServer, Compose UI test, test-only UI Automator, minSdk 23, and targetSdk 36.

## Verified Implementation Status

Status last audited on 2026-08-03 from current source, tests, merged commits,
and staging evidence. Tasks 1A–5 retain their recorded authorities below. Task 6
candidate `acd6769c` and PASS evidence commit `10a55c1` merged via PR #15 as
`c96ade7f`. Fresh Gradle, API 23/API 36, API 25 focused, process-death, and
`mobile-pos-response-drop/v1` evidence covers the cumulative Android
implementation through Task 6. Task status, not unchecked execution-step boxes
below, records current completion.

| Task | Status | Evidence summary |
| --- | --- | --- |
| 1A | Completed | The dependency/compile-platform correction is present; clean unit, lint, debug, and release verification passes. The correction was committed with Task 1B rather than as an isolated Task 1A diff. |
| 1B | Completed | Test Core/Runner 1.7.0 alignment; clean `./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease` PASS (101 tasks); exact API 23 and API 36 device runs PASS with 3/3 tests each; deterministic evidence captured under `app/build/reports/mobile-pos-devices/api23` and `app/build/reports/mobile-pos-devices/api36`. |
| 2 | Completed | Commit `f49f624` enforces canonical HTTPS origin and a closed 14-endpoint catalog, validates request fields and four idempotent mutations, parses stable/native responses, classifies timeout/cancellation, aligns DTO snapshots, records fixture provenance, and passes 27 focused/full debug unit tests plus a clean 101-task debug/release unit, lint, and assemble gate. AGP 9.2.1 exposes no `testReleaseUnitTest` task; release Kotlin compilation and `assembleRelease` pass. |
| 2B | Completed | Uncommitted implementation replaces the placeholder XML/ViewBinding shell with Compose BOM `2026.06.00`, Material 3 semantic light/dark themes and Blue/Teal accents, five unique root destinations, elevated Cashier action, save/restore navigation, compact/expanded width behavior, honest unavailable states, debug previews, and release-fixture exclusion. On 2026-07-30, all five Android Studio Quail previews rendered and passed visual/semantics inspection; 32 debug unit tests, debug/release lint, debug/release assembly, release Kotlin compilation, Android-test APK assembly, and exact API 23/API 36 runs with 5/5 Compose tests each passed. AGP 9.2.1 exposes no `testReleaseUnitTest` task. |
| 2C | Completed | Commit `3801d03`, merged via `3352ddd`. Dashboard/Products states, adaptive layouts, honest unavailable release wiring, debug previews, and unit/Compose UI tests exist. Cumulative fresh verification through Task 2E passes the full Gradle and API 23/API 36 gates. Complete live aggregates remain unavailable. |
| 2D | Completed | Commit `ee56e73`, merged via `ef491fd`. Cashier/cart/checkout/receipt states, bounded rows, manual/HID barcode input, adaptive cart layouts, honest disabled payment confirmation, server-receipt display, debug previews, and unit/Compose UI tests exist. Cumulative fresh verification through Task 2E passes the full Gradle and API 23/API 36 gates. |
| 2E | Completed | Candidate `a391411` plus a test-only API 23 visibility fix, a chart slot alignment fix, and a debug-only `Demo layout` shell toggle. Reports/More states, application-private theme persistence, shell wiring, debug previews, and unit/Compose UI tests exist. Clean Gradle gate passed 100 tasks; API 23 and API 36 each passed 41 tests. Complete live reports remain unavailable; the populated shell is debug-only, defaults off, and is absent from release. |
| 3 | Completed | Commit `7a0aa51` implements OAuth Authorization Code + PKCE, encrypted attempt/token storage, App Link redirect handling, restart/logout/stale-callback behavior, and authenticated bootstrap. Final stable-origin verification passed on 2026-08-01; see `task-3-gate-record.md`. |
| 4 | Completed | Uncommitted implementation adds bootstrap/profile routing, coalesced capability ownership, ordered logout cleanup, XML/ViewBinding profile/root UI, fixtures, and tests. Final API 23/API 36 gates and corrected AGP 9 release unit-test gate passed on 2026-08-02; see `session-handoff-task-4.md`. |
| 5 | Completed | Commit `aa9d897`, PR #11, merged as `da28b57874e7f1840eab20af201379ff95c76148`. Accepted evidence: `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew lintRelease`, `./gradlew assembleDebug`, `./gradlew assembleRelease`, API 23 device suite (98 tests passed), API 36 device suite (98 tests passed), API 23 two-process recovery harness (PASS), and `git diff --check`. |
| 6 | Completed | PR #15 merged as `c96ade7f`. Candidate `acd6769c` adds opening UI/repository behavior, server-driven payment modes, decimal validation, durable opening recovery, session reconciliation, capability refresh, fixtures, and tests. Main-thread recovered reconciliation defect evidence remains preserved as FAIL; corrected API 25 `mobile-pos-response-drop/v1` evidence records exact UUID/body replay, one backend logical result, one `sessions.current`, one capability refresh, no new crash, and targeted staging cleanup PASS. Fresh verification passed debug unit/lint/assemble, exposed release unit task, release lint/assemble, Android-test assembly, API 23/API 36 suites (103 tests each), API 25 focused thread test, and API 23 process-death harness. |
| 7 | Completed | Customer search is debounced, cancellable, offset-paginated, bounded to 100 distinct records, scoped to profile/cashier, clears on logout and profile change, and has API 23/API 36 coverage. |
| 8 | Not Started | Catalog/cart UI/repository behavior, tests, fixtures, and accessibility harness are absent. |
| 9 | Not Started | Payment/receipt UI/repository behavior, tests, and fixtures are absent. |
| 10 | Not Started | History/return UI/repository behavior, tests, and fixtures are absent. |
| 11 | Not Started | Closing UI/repository behavior, tests, and fixtures are absent. |
| 12 | Not Started | Final lifecycle, performance, accessibility, release-inspection tests and harnesses are absent. |

Task 7 is complete through PR #18. Task 8 is approved and in progress under
its separate implementation approval; later tasks remain unauthorized.

## Global Constraints

- Do not implement any task until this documentation set is explicitly approved.
- Do not begin a later task or phase without explicit approval.
- Do not commit, push, publish, deploy, or provision production credentials
  without separate explicit approval.
- Use Kotlin, Jetpack Compose, and Material 3 for new UI work after Task 2B.
- Keep Task 1B's XML/ViewBinding shell as historical evidence until Task 2B replaces it.
- Keep `minSdk 23`.
- Keep fake records in debug previews or test source sets only; release runtime never presents mock data as ERPNext integration.
- Use the pinned reference repository only for licensed visual and interaction analysis; do not copy branding, database, authentication, business logic, or offline architecture.
- Call only the approved versioned Mobile POS API.
- Treat the fixed canonical-origin OAuth authorize and token routes as the only
  authentication control-plane exception; do not use discovery or another
  generic Frappe method.
- Do not calculate or persist authoritative accounting totals locally.
- Do not implement offline sale submission.
- Generate and persist one lowercase UUID and exact request body before each
  logical mutation.
- Reuse that UUID and body after an unknown result.
- Serialize each mutation DTO once as UTF-8 JSON and persist the exact bytes,
  content type, serializer identity, and local format version. Never reconstruct
  replay bytes; unknown formats enter manual recovery.
- A known-offline new mutation creates no UUID, row, worker, or transport call.
- Accept exact settlement only. Overpayment and local change calculation remain
  unsupported until separately contracted; receipt UI may display only a
  server-returned `change_amount`.
- Keep discount editing absent or disabled until a versioned contract supplies an
  authoritative input and validation flow; server-returned discounts are display-only.
- Keep camera scanning, printer integration, and synchronization controls absent
  or disabled until separately approved.
- Do not derive complete Dashboard or Reports aggregates from bounded API pages.
  Unsupported live sections remain unavailable; populated designs live only in
  debug previews marked `Demo data`.
- Capability refresh is event-triggered and coalesced.
- Bind each pending mutation to its HTTPS origin and OAuth client identity.
- Keep DTOs separate from domain and UI models.
- Use TDD for authentication, parsing, mutation, and recovery behavior.
- Preserve API 23, accessibility, security, and low-end performance.
- Ponytail is a complementary full-intensity simplicity constraint.
- Add no DI framework, ORM, Retrofit, image framework, or speculative module.
- Every task ends with fresh verification, intended diff review, and a stop for
  approval.
- Tasks execute serially in this exact order: 1A, 1B, 2, 2B, 2C, 2D, 2E, 3,
  4, 5, 6, 7, 8, 9, 10, 11, 12. Approval of one task never authorizes the next.

## Baseline Audit Resolution

The former Phase 0 `:app:checkDebugAarMetadata` blocker is resolved. Current
source compiles against Android 36.1, preserves `minSdk 23` and `targetSdk 36`,
and uses dependency versions compatible with that platform. Fresh clean unit,
lint, debug, and release verification passed on 2026-07-29. The correction was
included in commit `1bb1bd0` with the Task 1B Compose-to-Views change rather than
in an isolated Task 1A commit.

## External Hard Stops

- Task 3 requires approved debug or staging App Link, origin, client,
  application ID, redirect, signing association, scope, non-production test
  cashier, configuration provisioning method, and OAuth-attempt lifetime for
  the exercised variant.
- Production App Link remains blocked until production signing ownership and
  fingerprints are approved.
- Task 6 requires backend payment-mode metadata for opening balances.
- Tasks accepting decimal input require approved locale syntax, precision,
  scale, bounds, and no-rounding behavior.
- Task 8 serial-change quote and non-serial quantity behavior are governed by
  the approved decisions recorded in the Task 8 section below. Future mutation
  amount or quantity inputs still require their own contract review.
- Task 9 requires backend payment-mode metadata and an authoritative cart
  payable workflow.
- Task 9 supports exact settlement only. Overpayment/change remains blocked
  until explicit contract approval.
- Task 10 requires an authoritative return-refund workflow.
- Lost-response staging evidence requires an externally owned approved fault
  gate for all four mutations. Android does not own staging ingress
  configuration. Each operation must prove one UUID and exactly one business
  document after a post-upstream-completion response drop.
- Task 11 requires an externally owned deterministic staging queued-closing
  procedure with no Android endpoint or production test hook.
- Task 12 cannot complete until launch-p95, request-p95, UI-flow-p95, and
  PSS-growth thresholds are explicitly approved.
- Task 12 requires an approved representative low-end physical device and an
  external non-empty release denylist.
- Camera scanning, printer integration, synchronization controls, R8 changes,
  distribution, and production signing are outside this plan.
- Complete Dashboard and Reports aggregates require a separately approved backend
  contract. Tasks 2C through 2E may create debug-only populated previews, but
  release runtime must not claim complete analytics from the current 14 endpoints.
- Editable discount input requires a separately approved versioned contract;
  server-returned discount values remain display-only.

## Separate Backend Documentation Follow-Up

Do not begin backend documentation work from Android Phase 0 approval. A
separate user request is required, and its diff must not be mixed with this
Android diff.

The factual follow-up may remove stale statements that the Android plan does not
exist from backend `architecture.md`, `integration-boundaries.md`,
`testing-strategy.md`, and `implementation-plan.md`. Product or contract
decisions such as overpayment, payable/refund workflows, serial quote behavior,
performance, distribution, and staging operations require their own approval
before any backend PRD or contract update.

The backend follow-up requires its own diff review and explicit approval. It
does not modify backend application code, contracts, staging, or deployment.

## Backend Phase Gates

| Android phase | Backend requirement |
| --- | --- |
| Phase 1 | Backend Phase 1 stable envelope and fixtures |
| Phase 2 | Backend Phase 3 OAuth, route gate, and bootstrap |
| Phase 3 | Backend Phase 2 idempotency and Phase 4 sessions |
| Phase 4 | Backend Phase 4 customers and Phase 5 catalog |
| Phase 5 | Backend Phase 6 sale plus resolved payable contract |
| Phase 6 | Backend Phase 6 history/return plus resolved refund contract |
| Phase 7 | Backend Phase 7 closing |
| Final | Backend Final staging evidence |

## Planned File Map

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt` | Manual application container |
| `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt` | Compose host only after Task 2B |
| `app/src/main/java/com/rotiropi/pos_erpnext/auth/*` | OAuth, encrypted active attempt, and encrypted tokens |
| `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CanonicalBackendOrigin.kt` | Canonical origin parser |
| `app/src/main/java/com/rotiropi/pos_erpnext/data/api/*` | HTTPS transport, envelopes, and DTOs |
| `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt` | Endpoint and model boundary |
| `app/src/main/java/com/rotiropi/pos_erpnext/data/ConnectivityStatus.kt` | Conservative platform connectivity snapshot |
| `app/src/main/java/com/rotiropi/pos_erpnext/recovery/*` | Pending store, executor, and worker |
| `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt` | Unresolved-state guard and complete local cleanup |
| `app/src/main/java/com/rotiropi/pos_erpnext/ui/theme/*` | Compose semantic tokens, typography, shapes, spacing, and light/dark theme |
| `app/src/main/java/com/rotiropi/pos_erpnext/ui/components/*` | Focused reusable Compose components |
| `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/*` | Navigation Compose destinations, host, and bottom bar |
| `app/src/main/java/com/rotiropi/pos_erpnext/ui/*` | Compose features and immutable ViewModel state |
| `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/*` | Synthetic populated preview fixtures marked `Demo data` |
| `app/src/test/resources/api/v1/endpoint-contracts.json` | Parameter table for all 14 approved endpoints |
| `app/src/test/resources/api/v1/*` | Reviewed contract fixtures |
| `app/src/test/*` | Unit and HTTP integration tests |
| `app/src/androidTest/*` | Compose UI, Keystore, redirect, lifecycle, and accessibility tests |
| `tools/create-test-avds.sh` | Deterministic API 23/API 36 AVD creation |
| `tools/run-device-tests.sh` | Serial-pinned deterministic device matrix runner |
| `tools/oauth-process-death.sh` | Two-process OAuth-attempt harness |
| `tools/recovery-process-death.sh` | Two-process ADB recovery harness |
| `tools/performance-harness.sh` | Repeatable fake-response performance and PSS runner |
| `tools/accessibility-harness.sh` | Deterministic accessibility matrix and evidence runner |
| `tools/verify-release-artifacts.sh` | Reproducible release APK and denylist inspection |

## Required Verification and Review Stop

Unless a task explicitly has no Android UI/device output, its green gate repeats
the six Gradle and two serial-pinned device commands below after targeted tests.
Every review stop then runs the two repository inspection commands shown last:

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
git diff --check
git status --short
```

An unavailable required device, image, backend fixture, staging procedure, or
external evidence is a blocker, not a skipped check. Every task then inspects
only its listed files, reports its proposed commit message, performs no commit,
and waits for separate approval.

---

## Android Phase 1: Platform and API Foundation

### Task 1A: Correct the Gradle and Build Baseline

**Status:** Completed — audited 2026-07-29; implementation commit `1bb1bd0`.

**Audit evidence:** Current `compileSdk` is 36.1 with `minSdk 23` and
`targetSdk 36`; the incompatible Compose-era dependency versions are absent.
`./gradlew clean test lint assembleDebug` and
`./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
both passed. No standalone Task 1A commit/review exists because `1bb1bd0`
combined the baseline correction with Task 1B UI work.

**Depends on:** Explicit Android Phase 0 documentation approval.

**Backend gate:** None beyond approved Phase 0 documentation.

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Produces:**

- Reviewed dependency and compile SDK baseline.
- Passing unit, lint, debug, and release builds.
- One recorded compile-platform source of truth consumed by Task 1B tooling.
- No Compose removal or UI change.

- [ ] **Step 1: Inspect and correct the dependency baseline**

```bash
./gradlew :app:dependencies
./gradlew clean
```

Choose the minimum API 23-compatible dependency correction for Android 36.1 or
propose `compileSdk 37` for explicit review. Do not change `minSdk 23` or
`targetSdk 36` merely to satisfy metadata, and do not remove Compose in Task 1A.

- [ ] **Step 2: Verify Task 1A**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
```

Expected: every command exits 0 and AAR metadata checks pass.

**Acceptance criteria:** The selected dependencies and compile platform are
compatible, `minSdk 23` and `targetSdk 36` remain unchanged, clean debug/release
unit, lint, and assemble gates pass, and no Compose or UI change is present.

- [ ] **Step 3: Mandatory review and stop**

```bash
git status --short
git diff -- gradle/libs.versions.toml app/build.gradle.kts
```

Expected: only the minimum dependency or compile SDK correction is present.
Report the proposed commit message `build: restore Android dependency
compatibility`. Do not commit and do not begin Task 1B without explicit
approval.

### Task 1B: Replace Compose with XML Views and ViewBinding

**Status:** Completed — audited 2026-07-29; implementation commit `1bb1bd0`.

**Audit evidence:** AndroidX Test Core/Runner aligned at 1.7.0, resolving the
instrumentation runtime mismatch. Clean `./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
passed with 101 tasks. `./tools/run-device-tests.sh api23` passed with exact
API 23 `3/3` tests; `./tools/run-device-tests.sh api36` passed with exact API 36
`3/3` tests. Evidence is preserved under
`app/build/reports/mobile-pos-devices/api23` and
`app/build/reports/mobile-pos-devices/api36`. `./tools/create-test-avds.sh`
verified compile platform 36.1, API 23 Google APIs arm64 revision 33, API 36
Google APIs arm64 revision 7, and emulator 36.6.11. Source, configuration, and
resolved dependencies contain no Compose artifact. Manifest controls enforce
`allowBackup="false"` and `usesCleartextTraffic="false"`.

**Depends on:** Approved and passing Task 1A.

**Backend gate:** Approved and passing Task 1A baseline.

**Files:**

- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Delete: `app/src/main/java/com/rotiropi/pos_erpnext/ui/theme/Color.kt`
- Delete: `app/src/main/java/com/rotiropi/pos_erpnext/ui/theme/Theme.kt`
- Delete: `app/src/main/java/com/rotiropi/pos_erpnext/ui/theme/Type.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/fragment_sign_in.xml`
- Create: `app/src/main/res/navigation/mobile_pos_nav_graph.xml`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/auth/SignInFragment.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ViewBindingLifecycleTest.kt`
- Create: `tools/create-test-avds.sh`
- Create: `tools/run-device-tests.sh`
- Replace: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ExampleInstrumentedTest.kt`

**Produces:**

- XML/ViewBinding application shell.
- One activity and navigation host.
- Accessible sign-in destination.
- Backup disabled and cleartext rejected.
- No Compose plugin, source, test, or dependency.
- Deterministic API 23 and API 36 device runners.

- [ ] **Step 1: Create deterministic device scripts**

Require the documented API 23 and API 36 images. Detect the host ABI, create the
fixed AVD names, and fail rather than selecting another API, ABI, or ambient
device. Apply the exact CPU, memory, display, density, locale, timezone,
animation, wipe, snapshot, boot-timeout, and readiness settings from
`testing-strategy.md`. Read and verify the actual Task 1A compile platform rather
than duplicating it. Record the system-image revision and emulator version.

- [ ] **Step 2: Write the failing XML launch test**

Create an Espresso test named `launch_displays_xml_sign_in_destination` that
starts `MainActivity` and checks that a visible view contains the text
`Sign in`. This compiles against the existing resources and fails behaviorally
against the generated `Hello Android!` screen.

Create a focused lifecycle test proving Fragment ViewBinding is inaccessible
after `onDestroyView` and that recreation does not retain the destroyed view.

- [ ] **Step 3: Run the red check**

```bash
./tools/create-test-avds.sh
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: FAIL because no visible view displays `Sign in`, not because of AAR
metadata, a missing symbol, or an unspecified device. Stop if either required
system image is unavailable.

- [ ] **Step 4: Replace the starter minimally**

Remove Compose configuration and source, enable `viewBinding = true`, add only
the XML/navigation dependencies, host `NavHostFragment`, declare
`android.permission.INTERNET`, set `usesCleartextTraffic="false"`, set
`allowBackup="false"`, and exclude all app data from backup and device transfer.

- [ ] **Step 5: Verify the XML/ViewBinding baseline**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew :app:dependencies
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command passes and dependency output contains no Compose
artifact.

**Acceptance criteria:** The application launches an accessible XML sign-in
destination on API 23 and API 36, ViewBinding follows the view lifecycle, backup
and cleartext paths are disabled, the exact compile platform is available, and
the source and dependency graph contain no Compose application/test artifact.

- [ ] **Step 6: Review and stop**

Inspect `git status --short`, `git diff`, and `./gradlew :app:dependencies`.
Report the proposed commit message `refactor: replace Compose starter with XML
views`. Do not commit or begin Task 2 without explicit approval.

### Task 2: Implement the API Envelope and Transport Boundary

**Status:** Completed — initial implementation commit `e8fdba6`; completion commit
`f49f624`.

**Audit evidence:** Current source enforces the canonical HTTPS origin and a
closed production catalog matching all 14 contract rows. Request factories
validate query/body placement, required/optional fields, Bearer presence, and
lowercase UUID idempotency on exactly four mutations. Transport performs one
cancellable OkHttp dispatch with redirects and automatic retries disabled,
parses stable success/error envelopes before native status classification,
preserves valid `Retry-After`, exposes only allowlisted log events, and maps
malformed/incompatible responses safely. DTO parser snapshots match the checked-in
contract, preserve decimal strings, reject missing required fields, and map
unknown statuses to `UNSUPPORTED`. Fixture provenance records backend SHA
`b2a09d2` and excludes credentials/production PII/runtime-evidence claims.

Fresh focused and full debug unit runs passed 27 tests with zero failures. Clean
`./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
passed 101 tasks (100 executed, one up-to-date). AGP 9.2.1 exposes no
`testReleaseUnitTest` task in this project; release Kotlin compilation and
assemble evidence passed. Task 2 has no Android UI/device output, so API 23/API 36
device runs were not required.

**Depends on:** Approved and passing Task 1B.

**Backend gate:** Backend Phase 1 stable envelope plus approved Phase 0 contract
examples for the exact 14-endpoint table. These examples do not claim runtime
integration for a later backend phase.

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/ApiEnvelope.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/ApiFailure.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CanonicalBackendOrigin.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/EndpointContract.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/MobilePosApiClient.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/BootstrapDtos.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/SessionDtos.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CustomerDtos.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CatalogDtos.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/SalesDtos.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/ClosingDtos.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/api/ApiEnvelopeTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/api/CanonicalBackendOriginTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/api/MobilePosApiContractTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/api/MobilePosApiClientTest.kt`
- Create: `app/src/test/resources/api/v1/endpoint-contracts.json`
- Create: `app/src/test/resources/api/v1/envelope-success.json`
- Create: `app/src/test/resources/api/v1/envelope-error.json`
- Create: `app/src/test/resources/api/v1/native-401.json`
- Create: `app/src/test/resources/api/v1/native-403.json`
- Create: `app/src/test/resources/api/v1/native-404.json`
- Create: `app/src/test/resources/api/v1/native-429.json`
- Create: `app/src/test/resources/api/v1/native-500.json`
- Create: `app/src/test/resources/api/v1/native-503.json`
- Create: `app/src/test/resources/api/v1/malformed-response.json`
- Create: `app/src/test/resources/api/v1/incompatible-api-version.json`
- Create: `app/src/test/resources/api/v1/additive-fields.json`
- Create: `app/src/test/resources/api/v1/unknown-enum.json`

**Interfaces:**

- Produces `ApiResult.Success`, `ApiResult.ExpectedFailure`,
  `ApiResult.TransportFailure`, and `ApiResult.ProtocolFailure`.
- Produces `MobilePosApiClient.execute(request, deserializer)` using its OkHttp
  client directly.
- Produces typed network DTOs and one checked-in contract table for all 14
  approved endpoints.
- Contract-example DTOs/fixtures for blocked flows are parser snapshots, not
  final runtime integration evidence; their feature tasks may update them only
  after the corresponding backend gate.

- [ ] **Step 1: Write failing parser and transport tests**

Cover outer `message`, additive fields, decimal strings, stable error, native
401/403/404/429/500/503, `Retry-After`, request ID, redacted headers,
cancellation, malformed body, and missing or incompatible API major version.
Cover every canonical-origin invariant from `authentication.md`, additive
fields, and unknown enum mapping to an unsupported state.

Run one parameterized suite from `endpoint-contracts.json` covering all 14
methods, exact versioned paths, query/body encoding, authentication,
idempotency, automatic retry class, request fields, DTO optionality,
success/error envelopes, decimal strings, and additive compatibility. Assert no
health or return-preview row exists.

Test every canonical accepted and rejected example from `authentication.md`.

- [ ] **Step 2: Run the red tests**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.data.api.*"
```

Expected: FAIL because API types do not exist.

- [ ] **Step 3: Implement the minimum boundary**

Add OkHttp and Kotlin serialization only. Use MockWebServer against
`MobilePosApiClient` directly; do not add a one-implementation transport
interface. Construct URLs only from `CanonicalBackendOrigin` and the checked-in
endpoint table. Disable redirects, reject arbitrary absolute URLs, configure
unknown-field tolerance and explicit timeouts, perform no mutation auto-retry,
and use an allowlisted logger that never emits headers or request bodies.

Record fixture endpoint, backend phase, source type, backend SHA/version, review
reference, and no-credential/no-production-PII assertion. Review each future
feature DTO change with its refreshed fixture.

- [ ] **Step 4: Run the Phase 1 gate**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
```

Expected: PASS.

**Acceptance criteria:** Exactly 14 unique business endpoint rows and four
idempotent mutations are present; all parser, path, method, retry, DTO,
compatibility, native-error, and origin assertions pass; no health,
return-preview, arbitrary URL, credential logging, redirect, or mutation
auto-retry path exists.

- [ ] **Step 5: Review and stop**

Inspect the intended diff and report `feat: add mobile POS API transport`.
Wait for explicit approval before commit or Task 2B.

### Task 2B: Migrate the Verified Shell to Compose

**Status:** Completed — audited 2026-07-30 from the uncommitted implementation
and fresh blocking verification. Task 1B remains completed historical evidence;
this task replaces only its placeholder XML/ViewBinding shell after Task 2 passed.

**Audit evidence:** `MainActivity` now hosts Compose directly. Material 3 uses
semantic light/dark schemes, Blue/Teal accent choices, system sans-serif fallback,
and shared shape/spacing/touch-target tokens. Navigation defines exactly Home,
Products, Cashier, Reports, and More, uses single-top save/restore behavior, keeps
Cashier visually elevated, exposes selected-state semantics, switches compact and
expanded width layout bounds at 600 dp, and renders only honest unavailable
release states. Foundation previews exist only under `app/src/debug/`; unit tests
verify root uniqueness/order, width classification, theme accents/colors/shapes,
and release preview exclusion.

The XML layouts, Fragment, navigation graph, ViewBinding config/dependencies, and
old XML/ViewBinding tests are removed after parity. Fresh focused UI tests passed,
and the clean full available gate
`./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest compileReleaseKotlin`
passed 129 tasks with 32 debug unit tests. `testReleaseUnitTest` remains unavailable
under AGP 9.2.1. Exact sequential `./tools/run-device-tests.sh api23` and `api36`
runs each passed five Compose launch, navigation, recreation, touch-target,
external-keyboard, and scanner-input-absence tests, with deterministic runtime
state confirmed. Their cleanup waits until the owned emulator serial leaves ADB
before the next API starts.

Android Studio Quail rendered all five foundation previews: phone light/Blue,
phone dark/Teal, phone landscape at font scale 1.5, tablet portrait, and tablet
landscape. PNG and semantics inspection confirmed correct light/dark surfaces,
distinct accents, bounded content, centered elevated Cashier action, five root
actions in visual order, unclipped primary labels, and the honest Home unavailable
state. No input, total, stock, payment, printer, sync, live report, fake ERPNext
data, or release fixture appeared. Compose semantics and device tests provide the
Task 2B accessibility/input evidence; full manual TalkBack journeys remain a Task
12 lifecycle gate rather than a Task 2B blocker.

**Depends on:** Approved and passing Task 2.

**Backend gate:** None beyond Task 2. Populated Dashboard, Cashier, Products,
Reports, and Settings data remains debug-preview-only until each existing feature
task and backend gate passes.

**Reference:** [`ui-ux-reference-design.md`](ui-ux-reference-design.md), based on
`jipraks/kasirgratisan` commit
`25c244027d7b9723f1b53a71649630e020e63413`. Use visual and interaction patterns
only; do not import React, Capacitor, Dexie, authentication, business logic,
offline architecture, or branding.

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/theme/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/components/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/placeholder/*`
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/*`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/*`
- Delete after equivalent tests pass: `app/src/main/res/layout/activity_main.xml`
- Delete after equivalent tests pass: `app/src/main/res/layout/fragment_sign_in.xml`
- Delete after equivalent tests pass: `app/src/main/res/navigation/mobile_pos_nav_graph.xml`
- Delete after equivalent tests pass: `app/src/main/java/com/rotiropi/pos_erpnext/ui/auth/SignInFragment.kt`
- Replace after equivalent tests pass: Task 1B XML launch and ViewBinding lifecycle instrumentation tests

**Produces:**

- Material 3 light/dark semantic theme, configurable accent, consistent spacing,
  radius, elevation, typography, and licensed icons/font.
- Safe-area-aware five-destination shell: Home, Products, elevated Cashier,
  Reports, and More.
- Phone, tablet, portrait, and landscape layouts.
- Focused reusable Compose components and immutable screen-state contracts.
- Debug previews for foundation tokens and shell states.
- Honest release unavailable placeholders for every unintegrated root destination.

- [ ] **Step 1: Resolve the Compose dependency set**

At execution time, use current official Android documentation to choose the
minimum Kotlin 2.2.10/AGP 9.2.1/API 23-compatible Compose BOM, Material 3,
Navigation Compose, Lifecycle/ViewModel Compose, and UI-test dependencies. Do not
add a DI framework, image framework, chart library, screenshot framework, ORM,
or reference runtime dependency.

- [ ] **Step 2: Write failing shell and design-system tests**

Cover the five root destinations, elevated Cashier action, tab back-stack
preservation, no duplicate destinations, light/dark semantic colors, accent
selection, safe-area insets, 48 dp targets, state descriptions, phone/tablet
layout switch, font scale 1.5, and release exclusion of debug fixtures.

- [ ] **Step 3: Implement the minimum Compose foundation**

Convert `MainActivity` to `setContent`, add semantic theme tokens, and create the
Navigation Compose shell. Bundle Plus Jakarta Sans only from a pinned
OFL-licensed source with its license notice; use a verified bundled Android
sans-serif fallback if API 23 fails. Use Material Icons or another licensed
Android set and copy no branding assets.

Create honest unavailable placeholders for Dashboard, Products, Cashier,
Reports, and More. Keep foundation preview data under `app/src/debug/`. Release
runtime must not claim live aggregates, stock, totals, payment, printer, or sync
support. Feature compositions belong to Tasks 2C through 2E.

- [ ] **Step 4: Remove the placeholder XML shell only after parity**

Delete the Task 1B placeholder layouts, Fragment, and navigation graph only after
Compose launch, recreation, navigation, semantics, backup, cleartext, API 23,
and API 36 checks replace their coverage. Do not rewrite Task 1B history.

- [ ] **Step 5: Verify Task 2B**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Render representative previews with the installed Android Studio or Android CLI
preview command after inspecting its current help. Inspect light/dark, phone,
tablet, portrait, landscape, font scale 1.5, TalkBack order, external keyboard,
and scanner-focus behavior.

**Acceptance criteria:** The native Compose shell and static visual states pass
API 23/API 36, accessibility, adaptive-layout, theme, navigation, and
release-fixture-exclusion checks. No unapproved backend endpoint, reference
runtime code, fake release data, local authoritative accounting, or unsupported
feature is active.

- [ ] **Step 6: Review and stop**

Inspect only the listed files, report `feat: add Compose POS foundation`, and
wait for explicit approval before commit or Task 2C.

### Task 2C: Add Dashboard and Products Visual Surfaces

**Status:** Completed — verified on 2026-07-30; commit `3801d03` merged via
`3352ddd`.

**Depends on:** Approved and passing Task 2B.

**Backend gate:** None for static visual states. Complete Dashboard aggregates and
complete low-stock data remain unavailable under the current contract.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/dashboard/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/products/*`
- Create: focused shared metric, search, category, product, stock, and state components
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/DashboardPreviews.kt`
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/ProductsPreviews.kt`
- Create: targeted unit and Compose UI tests

- [ ] **Step 1: Write failing immutable-state and UI tests**

Cover loading, empty, populated preview, offline, unavailable, and error states;
phone/tablet grids; ERPNext snapshot labels; quick-action semantics; 48 dp targets;
and release exclusion of populated preview fixtures.

- [ ] **Step 2: Implement Dashboard visual composition**

Add outlet identity, sales/transaction KPI slots, capability quick-action slots,
recent-transaction slots, and low-stock slots. Keep complete aggregate and
low-stock values unavailable in release runtime. Never sum a bounded API page and
label it as a complete day.

- [ ] **Step 3: Implement Products visual composition**

Add search, category filtering, adaptive list/grid, product detail, image
placeholder, Item/Item Group identity, Price List/Item Price snapshot, Warehouse,
and stock snapshot states. Product CRUD and local stock management remain absent.

- [ ] **Step 4: Verify and stop**

Run the full Gradle and API 23/API 36 gate, render representative previews, inspect
light/dark, portrait/landscape, tablet, font scale 1.5, and accessibility. Report
`feat: add Dashboard and Products UI` and wait before Task 2D.

### Task 2D: Add Cashier, Cart, Checkout, and Receipt Visual Surfaces

**Status:** Completed — verified on 2026-07-30; commit `ee56e73` merged via
`ef491fd`.

**Depends on:** Approved and passing Task 2C.

**Backend gate:** None for static visual states. Functional cart quote, payment,
and receipt integration remains gated by Tasks 7 through 9.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/*`
- Create: focused shared cart, quantity, payment-method, and result components
- Create: debug-only previews and targeted unit/Compose UI tests

- [ ] **Step 1: Write failing immutable-state and adaptive-layout tests**

Cover search, manual/HID barcode input, category chips, product grid, empty and
populated cart previews, phone floating cart summary and bottom sheet, expanded
persistent cart pane, quantity controls, offline-not-submitted, price-changed,
submitting, receipt, and error states.

- [ ] **Step 2: Implement the visual Cashier and cart flow**

Use server snapshot labels and a 50-row visual bound. Discount editing remains
absent or disabled; server-returned discounts are display-only. Camera capture is
absent.

- [ ] **Step 3: Implement exact-settlement checkout and receipt compositions**

Do not calculate authoritative payable, accept overpayment, or calculate change.
Confirm remains disabled without a future authoritative payable and server modes.
Receipt values come only from future terminal `SaleDetail`, including any
server-returned `change_amount`.

- [ ] **Step 4: Verify and stop**

Run the full Gradle and API 23/API 36 gate, render representative previews, and
inspect light/dark, adaptive cart layouts, font scale 1.5, TalkBack order, external
keyboard, and scanner focus. Report `feat: add Cashier and checkout UI` and wait
before Task 2E.

### Task 2E: Add Reports and More Visual Surfaces

**Status:** Completed — verified on 2026-07-31 from candidate `a391411` plus
the test-only API 23 visibility fix, a chart slot alignment fix, and a debug-only
`Demo layout` shell toggle. With explicit
SDK environment, clean
`./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
passed 100 tasks. `./tools/run-device-tests.sh api23` and
`./tools/run-device-tests.sh api36` each passed `OK (41 tests)`. Six Task 2E
previews rendered in Android Studio Quail and passed visual/semantics inspection
across compact/expanded, light/dark, Blue/Teal, portrait/landscape, and font scale
1.5 coverage. Chart bars, value labels, and axis labels now share one equal-width
slot helper (`chartBarSlot`), covered by a red-green verified regression test.
Debug builds expose a `Demo layout` chip under More that swaps every root
destination to `PosDemoStates`; it defaults off, so release and the honest-state
Compose tests are unaffected. `app/src/release/` supplies an unavailable
`PosDemoStates` stub, so no populated fixture is packaged.

**Depends on:** Approved and passing Task 2D.

**Backend gate:** None for static visual states. Complete reports remain
unavailable under the current 14-endpoint contract.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/reports/*`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/*`
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/demo/PosDemoStates.kt` and its
  `app/src/release/` unavailable stub
- Create: debug-only previews and targeted unit/Compose UI tests

- [ ] **Step 1: Write failing report/settings/theme tests**

Cover period tabs, KPI and chart semantics, textual chart summaries, unavailable
release state, grouped settings, outlet/user placeholders, theme mode and accent
persistence, and disabled/hidden printer and synchronization controls.

- [ ] **Step 2: Implement Reports visual composition**

Add KPI slots, semantic breakdown rows, a compact Canvas chart, and top-product
slots. Populated data remains debug-only and marked `Demo data`; release runtime
shows `Reports unavailable` until a complete approved contract exists.

- [ ] **Step 3: Implement More and theme settings**

Add grouped outlet, user/session, theme, printer, and synchronization rows. Theme
mode and accent use application-private `SharedPreferences`. Outlet/user become
live only after Task 4. Printer/synchronization remain hidden or `Not supported`.

- [ ] **Step 4: Verify and stop**

Run the full Gradle and API 23/API 36 gate, render previews, and inspect
light/dark, every accent, phone/tablet, portrait/landscape, font scale 1.5, and
accessibility. Report `feat: add Reports and More UI` and wait before Task 3.

---

## Android Phase 2: Authentication and Bootstrap

### Task 3: Implement OAuth PKCE and Token Storage

**Status:** Completed on 2026-08-01. Stable staging OAuth, trusted App Links,
real browser Authorization Code + PKCE, restart/logout/stale-callback behavior,
and final API 23/API 36 regression gates passed. Evidence: `task-3-gate-record.md`.

**Depends on:** Approved and passing Task 2E.

**Backend gate:** Backend Phase 3 plus approved redirect URI, base URL, client
ID, App Link association, and OAuth Client provisioning.

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/auth/OAuthConfiguration.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/auth/OAuthCoordinator.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/auth/OAuthAttemptStore.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/auth/TokenStore.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/auth/AuthCompletionActivity.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/ApiFailure.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/MobilePosApiClient.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/auth/SignInScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/auth/SignInViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/auth/OAuthCoordinatorTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/api/AuthenticatedMobilePosApiClientTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/auth/AuthRedirectSecurityTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/auth/OAuthAttemptStoreTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/auth/OAuthAttemptProcessDeathTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/auth/TokenStoreTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/auth/BrowserOAuthJourneyTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/test/SpecialHarnessOnly.kt`
- Modify: `tools/run-device-tests.sh`
- Create: `tools/oauth-process-death.sh`

**Produces:**

- `OAuthCoordinator.beginAuthorization()`.
- `OAuthCoordinator.handleCompletion(Intent)`.
- `OAuthCoordinator.refresh()`.
- `OAuthAttemptStore.read`, `write`, `consume`, and `clear`.
- `TokenStore.read`, `write`, and `clear`.
- An authenticated API client that sends bearer tokens only to the configured
  canonical origin, refreshes an eligible read once, and never automatically
  replays a mutation.

- [x] **Step 1: Confirm the hard gate**

For the debug or staging variant being exercised, record its exact canonical
origin, public client ID, fixed authorize/token paths, scope `all`, redirect URI,
application ID, signing-certificate fingerprint, OAuth allowlist,
`assetlinks.json` association, non-production test cashier, configuration
provisioning method, and approved attempt lifetime. Stop when any value or
external provisioning evidence is absent. Android does not provision the OAuth
client or cashier. Production remains a separate Final gate.

`docs/mobile-pos/task-3-gate-record.md` holds those recorded values and their
evidence. Explicit approval to begin Task 3 was given on 2026-07-31. As of
2026-08-01 all thirteen items are recorded and verified against the stable named-
tunnel public HTTPS origin, the 10-minute attempt lifetime is approved, and the
final Task 3 gate has passed. Task 3 completed on 2026-08-01. The record preserves
the earlier ephemeral-origin provisioning history separately.

- [x] **Step 2: Write failing OAuth and Keystore tests**

Test mandatory S256, no client secret, exact redirect/state validation,
single-use code, cold/warm completion, forged explicit Intent, duplicate
parameters, merged-manifest exported state, and App Link verification.

Test `OAuthAttemptStore` encrypted round trip, the approved attempt lifetime
(10 minutes only if that proposal is approved),
origin/client/redirect binding, atomic replacement, unique IV, malformed or
tampered data, consume-once, cancellation, terminal cleanup, logout, and
process-death restoration. Inject death after consumed persistence and after
token persistence; assert neither boundary performs a second exchange.
Assert a mismatched unsolicited callback preserves the original pending attempt
and expiry for the matching callback.

Test `TokenStore` unique IV, independent IV/tag/ciphertext tampering,
malformed/truncated/unknown-version data, atomic replacement, injected partial
write, origin/client binding, backup exclusion, key invalidation, logout, and
terminal cleanup.

Add API-client tests proving exact-origin Bearer attachment, no redirect or
cross-origin forwarding, one serialized refresh and retry for eligible reads,
one refresh for concurrent read 401 responses, and no second network dispatch
for mutation 401. Reject arbitrary absolute, cross-origin, and redirecting token
endpoints for both authorization-code exchange and refresh; accept only the
fixed Frappe token path on the canonical origin.
Accept authorization only at the fixed canonical-origin authorize path with
scope `all`; reject discovery, dynamic endpoints, nested URLs, redirects, and
cashier-editable endpoint values.

- [x] **Step 3: Run the red tests**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.auth.*"
./gradlew testDebugUnitTest \
  --tests "com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClientTest"
./tools/run-device-tests.sh api23
```

Expected: FAIL because authentication components do not exist.

- [x] **Step 4: Implement with AppAuth and Android Keystore**

Use AppAuth for browser/Custom Tab authorization and PKCE. Declare only
AppAuth's `RedirectUriReceiverActivity` as the exact exported BROWSABLE App Link
receiver. Deliver explicit completion/cancellation PendingIntents to the
non-exported `AuthCompletionActivity`, then apply identical coordinator
validation to cold and warm delivery.

Persist the active attempt in `OAuthAttemptStore` before browser launch. Encrypt
its canonical origin, client ID, state, verifier, redirect metadata, creation,
and expiry using an API 23-compatible AES-GCM Keystore key and `AtomicFile`.
Store tokens in a separately versioned encrypted record.

Annotate every host-script-controlled setup/verification method
`SpecialHarnessOnly`. Broad device runs exclude that annotation; the OAuth
script invokes exact class/method names and records isolated artifacts.

Persist `pending` or `consumed` in the attempt record. After restart, never
exchange a code from a consumed attempt: retain already persisted matching
tokens and clean up the attempt, otherwise require a new browser authorization.

Inject `TokenStore` and serialized refresh behavior directly into
`MobilePosApiClient`. Construct only allowlisted endpoint URLs, disable
redirects, and verify canonical origin before reading or attaching credentials.
An eligible read may refresh and retry once. Mutation 401 returns a typed
authentication-required result without another network dispatch; Task 5 maps it
durably to `auth_required`. Exchange and refresh only at
`<canonical-origin>/api/method/frappe.integrations.oauth2.get_token` with
redirects disabled.

- [x] **Step 5: Run OAuth process-death recovery**

```bash
./tools/oauth-process-death.sh api23
```

Expected: the first instrumentation invocation persists an attempt, the host
force-stops and relaunches the app, the second invocation restores and validates
the attempt, a test-only exchanger succeeds once, and terminal state deletes the
attempt. Run additional injected boundaries after consumed persistence and after
token persistence; both must prove no second exchange.

- [x] **Step 6: Verify App Links for the exercised environment**

Use `adb -s <serial> shell pm verify-app-links --re-verify <application-id>` on
API 36, then poll
`adb -s <serial> shell pm get-app-links <application-id>` to a terminal state
with a bounded timeout. Expected: only the exact approved host verifies for that
installed package and certificate. Preserve commands and output. Debug or
staging evidence does not satisfy production.

- [x] **Step 7: Verify and inspect secrets**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Inspect the merged manifest and APK configuration. Expected: PASS with no
client secret, token, verifier, or cleartext route.

Run the real debug or approved staging browser/App Link journey with UI Automator
because Espresso cannot cross the application boundary. UI Automator remains
test-only.

**Acceptance criteria:** The exercised environment passes exact fixed-route
PKCE, callback, process-death, token-store, exact-origin transport, App Link,
secret-inspection, API 23, and API 36 gates; host-only tests cannot run in the
broad suite; production identity is not implied.

- [x] **Step 8: Review and stop**

Report `feat: add secure OAuth PKCE authentication` and wait for approval.

### Task 4: Implement Bootstrap and Profile Selection

**Status:** Completed on 2026-08-02. Implementation, review, and verification are complete. The initial exact-once gate passed seven of eight commands and exposed the missing `testReleaseUnitTest` task; a separately approved follow-up enabled release unit tests under AGP 9.2.1, then passed 187/187. Changes remain uncommitted pending explicit approval.

**Depends on:** Approved and passing Task 3.

**Backend gate:** Verified `bootstrap.get` from Backend Phase 3.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/BootstrapDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/AppViewModel.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/dashboard/DashboardUiState.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/MoreScreen.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/SettingsUiState.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/BootstrapRepositoryTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionViewModelTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/session/LogoutCoordinatorTest.kt`
- Create: `app/src/test/resources/api/v1/bootstrap-one-profile.json`
- Create: `app/src/test/resources/api/v1/bootstrap-multiple-profiles.json`
- Create: `app/src/test/resources/api/v1/bootstrap-stale-opening.json`

**Produces:**

- `MobilePosRepository.bootstrap(profileName)`.
- `MobilePosRepository.refreshCapabilities(trigger)` with one coalesced in-flight
  request.
- `LogoutCoordinator.logout()` for credential/cache clearing and sign-in
  routing; Task 5 adds unresolved-mutation guarding.
- Profile and opening domain models.
- Capability-driven initial routing.

- [x] **Step 1: Write failing bootstrap tests**

Cover no profile, one profile, multiple profiles, selected profile, all
capabilities false without selection, stale opening warning, unknown fields,
401, known-offline startup, failed required refresh, coalesced concurrent
refreshes, no observer/render/failure loop, and logout routing.
Assert exactly one refresh after authentication success, authentication
recovery, profile selection, and profile change. Assert no refresh from
bootstrap completion, capability observation, rendering, or refresh failure.
Verify logout clears authentication, bootstrap, and repository memory before
routing to sign in.

- [x] **Step 2: Run the red tests**

```bash
./gradlew testDebugUnitTest --tests "*Bootstrap*" --tests "*ProfileSelection*"
```

Expected: FAIL on missing repository, routing, and ViewModels.

- [x] **Step 3: Implement bootstrap and routing**

Map DTOs to domain models, select a single profile automatically, require
selection for multiple profiles, expose `STALE_OPENING`, and use capabilities
only for UI availability. Make `MobilePosRepository` the sole in-memory
capability owner. Start every process with mutations disabled and refresh only
after the authoritative events listed in `api-integration.md`; failed required
refresh leaves mutations disabled until explicit Retry.

- [x] **Step 4: Verify Phase 2**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: PASS on API 23 and API 36. An unavailable required device or image
blocks Task 4 completion.

Final gate evidence from 2026-08-02 (each command executed exactly once):

- `./gradlew testDebugUnitTest`: PASS, 187 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew testReleaseUnitTest`: FAIL, exit 1; not rerun under the exact-once gate policy.
- `./gradlew lintDebug`: PASS.
- `./gradlew lintRelease`: PASS.
- `./gradlew assembleDebug`: PASS.
- `./gradlew assembleRelease`: PASS.
- `./tools/run-device-tests.sh api23`: PASS, 75/75 tests.
- `./tools/run-device-tests.sh api36`: PASS, 75/75 tests.

Separately approved follow-up evidence from 2026-08-02:

- Root cause: AGP 9 changed `android.onlyEnableUnitTestForTheTestedBuildType` from `false` to `true`, so only the tested debug unit-test component existed and `testReleaseUnitTest` was absent.
- Fix: `gradle.properties` now sets `android.onlyEnableUnitTestForTheTestedBuildType=false`, preserving the plan's debug and release unit-test gates.
- Corrected `./gradlew testReleaseUnitTest`: PASS, 187 tests, 0 failures, 0 errors, 0 skipped.
- Scoped independent review: APPROVE, no P0/P1 or lower-severity findings.

No commit, push, merge, deploy, or Task 5 work was performed.

**Acceptance criteria:** Bootstrap/profile routing, stale-opening display,
capability ownership, exact refresh triggers, coalescing, failure-disablement,
initial logout cleanup, and API 23/API 36 UI behavior pass with reviewed Backend
Phase 3 fixtures.

- [x] **Step 5: Review and stop**

Report `feat: add scoped mobile POS bootstrap` and wait for approval.

---

## Android Phase 3: Recovery and Opening

### Task 5: Implement Durable Mutation Recovery

**Status:** Completed — commit `aa9d897`, PR #11, merged as `da28b57874e7f1840eab20af201379ff95c76148`. Accepted evidence: `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew lintRelease`, `./gradlew assembleDebug`, `./gradlew assembleRelease`, API 23 device suite (98 tests passed), API 36 device suite (98 tests passed), API 23 two-process recovery harness (PASS), and `git diff --check`.

`testReleaseUnitTest was not part of the accepted Task 5 verification record at the time PR #11 was completed. No release-unit PASS is claimed for Task 5.`

**Depends on:** Approved and passing Task 4.

**Backend gate:** Backend Phase 2 idempotency contract.

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/data/ConnectivityStatus.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/recovery/PendingMutation.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/recovery/PendingMutationStore.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/recovery/RecoveryCoordinator.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/recovery/RetryPendingMutationWorker.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/recovery/RecoveryScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/recovery/RecoveryViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/recovery/RecoveryCoordinatorTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/data/ConnectivityStatusTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/recovery/PendingMutationSerializationTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/recovery/PendingMutationStoreTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/recovery/ProcessDeathHarnessTest.kt`
- Create: `tools/recovery-process-death.sh`

**Produces:**

- `RecoveryCoordinator.execute(spec, responseDeserializer)`.
- Encrypted `PendingMutationStore`.
- Unique WorkManager retry by transaction UUID.
- Conservative `Online`, `KnownOffline`, and `Unknown` connectivity snapshots.

- [ ] **Step 1: Write failing recovery tests**

Cover persist-before-send, lowercase UUID, identical-body replay, timeout, 401,
429/503, 500 exhaustion, terminal persistence, wrong-user/site/client
rejection, unique AES-GCM IVs, authentication-tag failure, key invalidation,
one unresolved mutation bound, and known-offline start creating no UUID, row,
worker, serialization, or transport call. Verify mutation 401 is persisted as
`auth_required` before authentication recovery and causes no hidden transport
retry.

Cover disconnect before body, disconnect after body, response-body loss,
timeout, exact dispatch counts, serialize-once replay, unknown body format,
stale `sending`, changed-action UUID, `IDEMPOTENCY_KEY_REUSED`, crash before UI
acknowledgment, and logout guarding for every unresolved state. Verify initial
dispatch plus five retries is at most six dispatches; valid HTTP
`Retry-After`, stable `retry_after_seconds`, local delay, invalid values,
persisted count, and next-eligible time follow `state-and-recovery.md`.
Include HTTP 409 `REQUEST_IN_PROGRESS` in the same persisted precedence,
attempt-count, and six-dispatch assertions.

Connectivity restoration alone never starts never-prepared work; explicit Retry
may. It may run one already persisted eligible worker. Ambiguous, stale,
local/LAN, captive, or inspection-failed connectivity is `Unknown`, not
`KnownOffline`.
Annotate process-death setup and verification methods `SpecialHarnessOnly`; the
host script invokes exact methods and broad device runs must exclude them.

- [ ] **Step 2: Run the red tests**

```bash
./gradlew testDebugUnitTest --tests "*RecoveryCoordinator*"
./tools/run-device-tests.sh api23
```

Expected: FAIL because the recovery boundary does not exist.

- [ ] **Step 3: Implement the smallest durable store**

Use `SQLiteOpenHelper` for metadata and Keystore AES-GCM for sensitive bodies.
Store a version, fresh random 96-bit IV, ciphertext, and authentication tag for
each write, atomically with mutation metadata. Bind each record to normalized
HTTPS origin and OAuth client identity. Add WorkManager only for
network-constrained durable retries. Do not add Room, a redundant plaintext
hash, or an offline queue.

Add `ACCESS_NETWORK_STATE`. Implement `ConnectivityStatus` with Android
`ConnectivityManager`/`NetworkCapabilities` and an injectable test fake. Map the
validated endpoint DTO once to UTF-8 JSON, persist exact bytes, content type,
serializer identity, and local format version, and never reserialize a replay.

Before UUID allocation or serialization, return `NotStartedOffline` when
connectivity is known unavailable. Preserve UI input and require explicit Retry.
Route every prepared mutation, including authentication recovery, through
`RecoveryCoordinator`; the API client never performs an invisible mutation
replay.

- [ ] **Step 4: Verify recovery**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: PASS for unit, crypto, and in-process instrumentation behavior.

**Acceptance criteria:** Persist-before-send, exact bytes/UUID, tri-state
connectivity, bounded retry, 401 pause, identity/origin/client binding, process
restart, terminal acknowledgment, crypto failure, logout guard, API 23, and API
36 checks pass; no offline queue or hidden transport retry exists.

- [ ] **Step 5: Run the two-process recovery harness**

```bash
./tools/recovery-process-death.sh api23
```

The first `adb shell am instrument` invocation persists a mutation and exits.
The script then force-stops and relaunches the app before a second instrumentation
invocation verifies the same site, user, UUID, and byte-equivalent body. A
single instrumentation runner or `ActivityScenario.recreate()` is not accepted
as process-death evidence.

- [ ] **Step 6: Review and stop**

Report `feat: add durable mobile POS recovery` and wait for approval.

### Task 6: Implement Opening

**Status:** Not Started. Opening payment-mode and decimal-input gates remain
active. The lost-response procedure below is approved before Task 6
implementation, but its runtime execution remains pending until the opening flow
exists. This approval does not authorize Task 6 implementation.

**Depends on:** Approved and passing Task 5.

**Backend gate:** Backend Phase 4 plus approved opening payment-mode projection,
decimal-input contract, and the approved externally owned lost-response procedure
below.

#### Approved Task 6 Lost-Response Staging Procedure

- **Protocol identifier:** `mobile-pos-response-drop/v1`
- **Approval:** Approved before Task 6 implementation. Runtime execution is
  **PENDING** until the opening flow exists; no runtime evidence is claimed here.
- **Procedure owner:** `Mobile POS staging operator / repository maintainer`
- **Evidence location:** `docs/mobile-pos/evidence/mobile-pos-response-drop/`
- **Evidence record path:**
  `docs/mobile-pos/evidence/mobile-pos-response-drop/<operator-evidence-id>.md`
- **Android authority:** `7479ab5c9fd02281a919a82f1e48036074b322ab`
- **Backend runtime authority:** `40d2f2b56c6aa92b363485487e58ccb3a62e334c`
- **Backend documentation/main authority:**
  `38728df32394aac1fc5c49387b2ea6e0f3b5c15b`

The owner performs this staging-only procedure after the Task 6 opening flow
exists:

1. Confirm the Android and backend checkouts match the authority SHAs above,
   choose a non-production test cashier and POS Profile, assign an operator
   evidence ID, and confirm no unrelated fault rule is active.
2. Capture the persisted `sessions.open` request UUID and a hash of its persisted
   request body before transmission. Arm one ingress fault for that request only.
   The fault is one-shot: it must not match or drop any later request.
3. Send the original request normally. Drop only its delivery to Android, and only
   after the backend has completed and committed the opening request. The fault
   must not change the request, including its UUID or body, or change the backend
   response status, headers, or body; it only prevents that completed response
   from reaching Android.
4. Remove the fault rule immediately after the one response drop and confirm it is
   absent before replay. Stop if removal cannot be confirmed.
5. Replay through the normal Android recovery path using the exact same
   idempotency UUID and exact persisted request body. Do not reconstruct or edit
   either value.
6. Record the replay result and the resulting POS Opening Entry identifier. Run a
   database or approved versioned-API query scoped to this operation and preserve
   sanitized output proving the result is exactly one POS Opening Entry.
7. Perform the backend-owned staging cleanup approved for the selected test
   cashier and POS Profile. Verify the fault rule remains absent, the staging
   profile has returned to its agreed pre-test state, and no test opening remains
   active. Record cleanup confirmation and a final `PASS` only when every check
   above succeeds; otherwise record `FAIL` with the failed step.

Each evidence record must contain:

- Protocol identifier.
- Operator evidence ID.
- Timestamp.
- Operator.
- Android commit SHA.
- Backend runtime SHA.
- Backend documentation/main SHA.
- Original request UUID.
- Replay request UUID.
- Persisted request-body hash.
- Resulting POS Opening Entry identifier.
- Database/API query and sanitized result proving exactly one entry.
- Original attempt result.
- Replay result.
- Cleanup confirmation.
- Final `PASS` or `FAIL` conclusion.

Evidence must contain no production data, credentials, tokens, cookies, or
customer PII. Store only the sanitized record under the evidence location above;
do not store request or response bodies. A `PASS` requires matching original and
replay UUIDs, the same persisted request-body hash, confirmed post-commit response
drop, confirmed rule removal, exactly one POS Opening Entry, and completed cleanup.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/SessionDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningViewModelTest.kt`
- Create: `app/src/test/resources/api/v1/session-current.json`
- Create: `app/src/test/resources/api/v1/session-opened.json`

**Produces:**

- `MobilePosRepository.currentSession`.
- `MobilePosRepository.openSession`.
- Recovery-safe opening UI.

- [ ] **Step 1: Confirm payment-mode metadata**

Stop if the backend cannot enumerate the modes needed by opening balances.
Also stop if locale syntax, precision, scale, bounds, no-rounding behavior, or
fixture provenance is unapproved, or if runtime execution of the approved
post-completion response-drop procedure cannot be performed after the opening
flow exists. Do not finalize DTO fields before this gate.

- [ ] **Step 2: Write failing opening tests**

Cover configured rows, decimal input, stale warning, double-tap suppression,
replay, 401, timeout, `SESSION_ALREADY_OPEN`, and current-session reconciliation.
Assert successful or replayed opening completion emits exactly one coalescible
capability-refresh trigger; rejection does not.
Verify logout clears opening balances/input and terminal acknowledgment behavior.

- [ ] **Step 3: Run the red tests**

```bash
./gradlew testDebugUnitTest --tests "*Opening*"
```

Expected: FAIL on missing opening components.

- [ ] **Step 4: Implement through RecoveryCoordinator**

Create rows only from server modes, persist before submit, disable duplicate UI
actions, navigate only from terminal server state, and request the documented
capability refresh after successful or replayed completion.

- [ ] **Step 5: Verify opening**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command exits 0 with reviewed Backend Phase 4 fixtures.

- [ ] **Step 6: Verify staging lost response**

Consume the externally operated one-shot fault condition. Preserve evidence
binding backend SHA, original/replay UUID, post-upstream-completion drop,
original Opening Entry reference, exactly one POS Opening Entry, operator
evidence ID, and approved protocol reference.

**Acceptance criteria:** Server-provided modes, decimal syntax, recovery,
reconciliation, capability refresh, logout cleanup, API 23/API 36 behavior, and
exactly-one Opening Entry evidence pass without local accounting.

- [ ] **Step 7: Review and stop**

Inspect only the listed files, report `feat: add recoverable POS opening`, and
wait for approval.

---

## Android Phase 4: Customer, Catalog, and Cart

### Task 7: Implement Customer Search

**Status:** Hotfix In Review. Post-merge audit fixes B1-B4 and I1-I2 cover
serialized search authority, logout ordering, walk-in selection, recoverable
pagination metadata, strict normalized-query no-op behavior, blank-open search,
and external-keyboard navigation. Task 8 work was implemented in a separate
worktree and reconciled onto latest main; this hotfix text predates that
reconciliation.

**Depends on:** Approved and passing Task 6.

**Backend gate:** Backend Phase 4 customer contract.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchSheet.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CustomerDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchViewModelTest.kt`
- Create: `app/src/test/resources/api/v1/customer-page.json`

**Produces:**

- `MobilePosRepository.searchCustomers(query, posProfile, page, pageLength)`
  mapped to `GET customers.search` query parameters `q`, `pos_profile`, `start`,
  and `limit`. The first offset is `start=0`; requests use `limit=20`, advance
  from returned offset metadata, use no page-number model, and retain at most
  100 distinct Customer records.

- Task 7 is an approved exception to the existing Compose restriction. It may
  add only the customer sheet, its Cashier integration, focused reusable
  components, previews, and tests; it does not authorize a global UI redesign
  or Task 8 work.

- [ ] **Step 1: Write failing customer tests**

Cover exact 300 ms debounce, cancellation, generation/identity/profile stale
response suppression, offset pagination, default walk-in selection, registered
selection, display-name visibility, empty state, initial and page retry, fixture
provenance, and logout cache clearing. A changed normalized query clears prior
records and restarts at offset zero. A successful identical normalized query is
a no-op; a failed query retries only after explicit cashier action. Page results
deduplicate by Customer `name`, preserve server ordering, and stop at
`has_more=false` or 100 records. Initial failure shows no old-query records;
page failure retains successful records and retries the same offset. Known
offline shows unavailable without a local customer cache or fallback endpoint.

- [ ] **Step 2: Run red tests**

```bash
./gradlew testDebugUnitTest --tests "*CustomerSearch*"
```

Expected: FAIL.

- [ ] **Step 3: Implement bounded customer selection**

Use offset page size 20, never create customers, and clear walk-in display name
when a registered customer is selected. Default walk-in identity comes only from
the selected profile `customer`; it is not hard-coded or derived from results.
Query, result, selection, and display-name state stay memory-only and clear on
logout, cashier change, or profile change.

- [ ] **Step 4: Verify customer search**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command exits 0.

**Acceptance criteria:** Search is debounced, cancellable, paginated to 20 by
default, bounded by 100, scoped, walk-in-safe, cache-cleared on logout, and
verified on API 23/API 36 without customer creation.

- [ ] **Step 5: Review and stop**

Inspect only the listed files, report `feat: add customer selection`, and wait
for approval.

### Task 8: Implement Catalog, Scan, Quote, and Cart

**Status:** Completed. Task 8 implementation merged via PR #20 (merge commit
`28ba8f50703dbb901fb7cbcb6cabf0d7bd1f089b`); Task 9 remains Not Started.

**Depends on:** Approved and passing Task 7.

**Backend gate:** Backend Phase 5 catalog contract plus the approved Task 8
serial-change quote and decimal quantity decisions below. No backend contract
change is required.

**Approved Task 8 decisions (2026-08-04):**

- Serial identity is not a `catalog.quote_item` pricing input. Send only the
  existing `pos_profile`, `customer`, `item_code`, `qty`, `uom`, and `batch_no`
  fields. Never add `serial_numbers`, `serial_no`, a generic Frappe fallback, or
  a new backend endpoint.
- Selecting, scanning, replacing, or removing a serial invalidates the prior
  quote authority, cancels the active quote when possible, updates local serial
  identity, and starts a fresh quote with the existing allowed fields. A result
  from an earlier serial generation can never be applied to the current line.
- Local quote authority includes cashier/session identity, POS Profile,
  customer, item code, quantity, UOM, batch, serial identity, generation, and
  request ID. The serial remains on the cart line for the future sale request;
  ERPNext validates it at transaction submission.
- A serial line represents exactly one unit, uses `qty = 1`, cannot be edited to
  a fractional or greater quantity, cannot merge, and cannot share its serial
  with another cart line. Replacing a serial requires a verified
  `catalog.scan` result. Non-serial rows merge only when item code, resolved
  UOM, and batch exactly match. Scan-provided warehouse, UOM, batch, and
  conversion factor are preserved without local inventory or accounting
  calculation.
- Non-serial quantity input accepts ASCII digits with one optional ASCII `.`,
  zero to six fractional digits, a value greater than zero, and a maximum of
  `999999.999999`. Comma decimals, grouping separators, signs, exponents,
  whitespace, `.5`, `1.`, and values with more than six fractional digits are
  rejected. Use exact decimal parsing, send canonical decimal-dot text, keep
  leading fractional zeroes, optionally remove trailing zeroes without value
  change, and never round or truncate. This decision applies to Task 8 cart
  quantities only; it does not authorize Task 9 or later payable/refund rules.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CartState.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CashierScreen.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CashierUiState.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CashierViewModel.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/ProductGrid.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CartContent.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/CatalogDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/cashier/CashierViewModelTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/cashier/CatalogAccessibilityTest.kt`
- Create: `app/src/test/resources/api/v1/catalog-page.json`
- Create: `app/src/test/resources/api/v1/catalog-scan.json`
- Create: `app/src/test/resources/api/v1/catalog-quote.json`
- Create: `tools/accessibility-harness.sh`

**Produces:**

- `MobilePosRepository.searchCatalog(...)`, `scanCatalog(...)`, and
  `quoteItem(...)` signatures derived only after the reviewed backend/serial
  gate. Do not add an uncontracted serial quote field.
- In-memory `CartState` capped at 50 distinct rows.

- [ ] **Step 1: Write failing catalog/cart tests**

Cover pagination, search cancellation, scanner input, UOM conversion,
batch/serial propagation, warnings, stale quote cancellation, quantity changes,
stock snapshot labeling, row merging by item/UOM/batch, serial uniqueness across
the cart, and rejection of the fifty-first cart row.
Cover the approved serial-change behavior, decimal syntax/no-rounding rule,
fixture provenance, and logout clearing catalog/cart/quote state.

- [ ] **Step 2: Run red tests**

```bash
./gradlew testDebugUnitTest --tests "*CashierViewModel*"
```

Expected: FAIL.

- [ ] **Step 3: Implement the minimal sale workspace**

Use HID/manual scan input, server-resolved identifiers, bounded RecyclerViews,
and estimate labels. Merge non-serialized rows only when item, resolved UOM,
and batch match; never place one serial in multiple rows. Do not add CameraX,
an image loader, inventory picker, or local authoritative total.

- [ ] **Step 4: Verify catalog and cart**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
./tools/accessibility-harness.sh api23
./tools/accessibility-harness.sh api36
```

Expected: every command exits 0 and scanner/accessibility artifacts pass.

**Acceptance criteria:** Search, scan, quote, UOM/batch/serial propagation,
stale cancellation, 50-row bound, exact merge/serial rules, logout cleanup,
scanner operation, accessibility, and API 23/API 36 behavior pass without local
inventory/accounting authority or an unapproved camera/image dependency.

- [ ] **Step 5: Review and stop**

Inspect only the listed files, report `feat: add catalog and bounded cart`, and
wait for approval.

---

## Android Phase 5: Sale, Payment, and Receipt

### Task 9: Implement Fully Settled Sale

**Status:** Not Started. Authoritative payable/payment-mode, exact-settlement,
decimal-input, and external lost-response gates remain active.

**Depends on:** Approved and passing Task 8.

**Backend gate:** Backend Phase 6, allowed payment-mode metadata, and an
authoritative cart payable workflow, approved exact-settlement/decimal rules,
and externally owned lost-response procedure.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/PaymentDialog.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/PaymentViewModel.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/ReceiptScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/ReceiptViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/SalesDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/payment/PaymentViewModelTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/receipt/ReceiptViewModelTest.kt`
- Create: `app/src/test/resources/api/v1/sale-success.json`
- Create: `app/src/test/resources/api/v1/sale-replayed.json`
- Create: `app/src/test/resources/api/v1/sale-price-changed.json`

**Produces:**

- `MobilePosRepository.submitSale(...)` and `getSale(...)` mapped only from the
  final reviewed payable/payment contract.
- Exact-settlement payment and server-only receipt UI.

- [ ] **Step 1: Confirm all sale gates**

Stop unless Android can receive authoritative payable values and valid payment
modes without local accounting. Also stop unless exact-only settlement is
aligned with the backend example, decimal syntax is approved, fixtures have
provenance, and the external post-completion response-drop protocol is approved.
Do not finalize payment DTO fields or repository signatures before this gate.

- [ ] **Step 2: Write failing payment/sale tests**

Cover exact settlement, multiple distinct modes, underpayment and overpayment
prevention, absence of local change calculation/presentation, double-tap
suppression, `PRICE_CHANGED`, 401, timeout, replayed success, process death, and
server-only receipt values. Assert successful or replayed sale completion emits
exactly one coalescible capability-refresh trigger; rejection does not.
Verify replay fixture compatibility, fixture provenance, terminal
acknowledgment, and logout clearing payment/cart/receipt state.

- [ ] **Step 3: Run red tests**

```bash
./gradlew testDebugUnitTest --tests "*Payment*" --tests "*Receipt*"
```

Expected: FAIL.

- [ ] **Step 4: Implement sale through RecoveryCoordinator**

Persist the complete request, submit once, treat price change as terminal
rejection requiring review, accept only payment rows summing exactly to the
authoritative payable value, render receipt only from `SaleDetail`, and request
the documented capability refresh after successful or replayed completion.

- [ ] **Step 5: Verify sale**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command exits 0 with the approved payable contract.

- [ ] **Step 6: Verify staging lost response**

An approved external operator triggers the one-shot response drop behind the
normal staging HTTPS ingress and supplies its evidence ID. Android owns no proxy,
certificate, credential, control endpoint, ingress change, or deployment
automation. Verify the replay uses the original transaction ID and returns the
original reference. Preserve backend SHA, original/replay UUID,
post-upstream-completion drop, original POS Invoice reference, exactly one POS
Invoice, operator evidence ID, and approved protocol reference.

**Acceptance criteria:** Authoritative payable/modes, exact settlement,
price-change rejection, durable replay, capability refresh, terminal
acknowledgment, logout cleanup, API 23/API 36 behavior, and exactly-one POS
Invoice evidence pass without local totals, overpayment, or change calculation.

- [ ] **Step 7: Review and stop**

Inspect only the listed files, report `feat: submit recoverable POS sales`, and
wait for approval.

---

## Android Phase 6: History and Return

### Task 10: Implement History and Return

**Status:** Not Started. Authoritative refund, decimal-input, and external
lost-response gates remain active.

**Depends on:** Approved and passing Task 9.

**Backend gate:** Backend Phase 6 plus an authoritative refund workflow,
approved decimal rules, and externally owned lost-response procedure.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/history/SaleDetailScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/returning/ReturnScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/returning/ReturnViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/SalesDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/history/HistoryViewModelTest.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/returning/ReturnViewModelTest.kt`
- Create: `app/src/test/resources/api/v1/sale-history.json`
- Create: `app/src/test/resources/api/v1/sale-detail.json`
- Create: `app/src/test/resources/api/v1/return-success.json`
- Create: `app/src/test/resources/api/v1/return-limit-exceeded.json`

**Produces:**

- `MobilePosRepository.listSales(...)` and `getSale(...)` from the reviewed
  history contract.
- `MobilePosRepository.createReturn(...)` only after the final
  remaining-returnable/refund contract is approved.

- [ ] **Step 1: Confirm the refund gate**

Stop unless the backend defines server-authoritative remaining quantities and
refund payment values or another approved workflow with equivalent safety. Also
stop unless decimal syntax, fixture provenance, and the external
post-completion response-drop protocol are approved. History DTOs may be
reviewed, but the combined task cannot complete and return interfaces cannot be
finalized before this gate.

- [ ] **Step 2: Write failing history/return tests**

Cover pagination, scope, walk-in name, required reason, row quantities,
`RETURN_LIMIT_EXCEEDED`, exact refund, replay, process death, and no
cancellation action. Assert successful or replayed return completion emits
exactly one coalescible capability-refresh trigger; rejection does not.
Verify `RETURN_LIMIT_EXCEEDED` fixture compatibility, terminal acknowledgment,
and logout clearing history, detail, return input, and receipt state.

- [ ] **Step 3: Run red tests**

```bash
./gradlew testDebugUnitTest --tests "*History*" --tests "*ReturnViewModel*"
```

Expected: FAIL.

- [ ] **Step 4: Implement paginated history and recoverable return**

Use POS Invoice only, display server values, submit through RecoveryCoordinator,
render the return receipt from terminal response, and request the documented
capability refresh after successful or replayed return completion.

- [ ] **Step 5: Verify history and return**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command exits 0 with the approved refund contract.

- [ ] **Step 6: Verify staging lost response**

Consume the externally operated one-shot fault condition. Preserve evidence
binding backend SHA, original/replay UUID, post-upstream-completion drop,
original return POS Invoice reference, exactly one return POS Invoice, operator
evidence ID, and approved protocol reference.

**Acceptance criteria:** Scoped paginated history, server-authoritative return
limits/refund, reason validation, durable replay, capability refresh, terminal
acknowledgment, logout cleanup, API 23/API 36 behavior, and exactly-one return
POS Invoice evidence pass without cancellation or local refund calculation.

- [ ] **Step 7: Review and stop**

Inspect only the listed files, report `feat: add POS history and returns`, and
wait for approval.

---

## Android Phase 7: Closing

### Task 11: Implement Closing and Status Recovery

**Status:** Not Started. Decimal-input, external lost-response, and deterministic
queued-closing gates remain active.

**Depends on:** Approved and passing Task 10.

**Backend gate:** Backend Phase 7 plus approved decimal rules, externally owned
lost-response procedure, and externally owned deterministic queued-closing
staging procedure.

**Files:**

- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/closing/ClosingScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/closing/ClosingViewModel.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/data/api/ClosingDtos.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/closing/ClosingViewModelTest.kt`
- Create: `app/src/test/resources/api/v1/closing-preview.json`
- Create: `app/src/test/resources/api/v1/closing-draft.json`
- Create: `app/src/test/resources/api/v1/closing-queued.json`
- Create: `app/src/test/resources/api/v1/closing-failed.json`
- Create: `app/src/test/resources/api/v1/closing-cancelled.json`
- Create: `app/src/test/resources/api/v1/closing-processing.json`
- Create: `app/src/test/resources/api/v1/closing-completed.json`
- Create: `app/src/test/resources/api/v1/closing-rejected.json`
- Create: `app/src/test/resources/api/v1/closing-submitted.json`

**Produces:**

- `MobilePosRepository.previewClosing(...)`, `submitClosing(...)`, and
  `closingStatus(...)` from the reviewed closing contract.

- [ ] **Step 1: Write failing closing tests**

Cover preview, counted balances, double tap, timeout, `REQUEST_IN_PROGRESS`,
transport-unknown `waiting_retry`, Processing submit replay, Completed stored
result, Rejected terminal behavior, bare Draft manual recovery, queued polling
only after Completed, process death, submitted, failed manager guidance,
cancelled manager-controlled terminal behavior, and bounded backoff. Assert one
coalescible capability refresh after accepted submission and after terminal
status recovery.
Assert foreground polling delays 2, 4, 8, 16, then 30 seconds, stops after five
minutes, schedules no status worker, preserves queued state, and resumes only
from explicit `Check status`. Verify terminal acknowledgment and logout clearing
preview, counted balances, queued/terminal status, and navigation state.

- [ ] **Step 2: Run red tests**

```bash
./gradlew testDebugUnitTest --tests "*Closing*"
```

Expected: FAIL.

- [ ] **Step 3: Implement closing recovery**

Submit through RecoveryCoordinator. Replay the exact submit only while the
local state is transport-unknown `waiting_retry` or persisted request
disposition is Processing; use the stored result when it is Completed; never
replay Rejected. A bare `draft` without disposition enters manual recovery. Poll
`closing.status` only when a Completed result is `queued`, treat `submitted`,
`failed`, and `cancelled` as terminal, stop foreground polling after five
minutes, emit only the documented capability-refresh triggers, and never create
a replacement closing. After five minutes, preserve queued state and require an
explicit `Check status`; do not enqueue an automatic status worker.

- [ ] **Step 4: Verify real queued behavior**

Consume the externally approved deterministic staging-only queued procedure.
It must use active workers, expose no Android endpoint or production test hook,
and require no Android control of ingress/workers. Record the real `queued` to
terminal transition and its operational evidence reference.

- [ ] **Step 5: Verify closing**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Expected: every command exits 0 with reviewed Backend Phase 7 fixtures.

- [ ] **Step 6: Verify staging lost response**

Consume the externally operated one-shot fault condition. Preserve evidence
binding backend SHA, original/replay UUID, post-upstream-completion drop,
original Closing Entry reference, exactly one POS Closing Entry, operator
evidence ID, and approved protocol reference.

**Acceptance criteria:** Preview/counts, Processing/Completed/Rejected,
transport-unknown replay, bare-Draft safety, queued/terminal behavior, bounded
explicit polling, capability refresh, terminal acknowledgment, logout cleanup,
API 23/API 36 behavior, deterministic queued evidence, and exactly-one Closing
Entry evidence pass without a replacement closing.

- [ ] **Step 7: Review and stop**

Inspect only the listed files, report `feat: add recoverable POS closing`, and
wait for approval.

---

## Android Final: Hardening and Release Evidence

### Task 12: Verify the Complete Android Lifecycle

**Status:** Not Started. Backend Final, staging evidence, performance thresholds,
representative device, release denylist, and production-readiness gates remain
active.

**Depends on:** Approved and passing Task 11.

**Backend gate:** Backend Final and approved staging environment.

**Files:**

- Replace: `app/src/test/java/com/rotiropi/pos_erpnext/ExampleUnitTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`
- Create: `app/src/androidTest/AndroidManifest.xml`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/MobilePosTestRunner.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/TestMobilePosApplication.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/test/TestHttpsFixture.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/MobilePosJourneyTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/AccessibilityJourneyTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/MobilePosPerformanceTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/LogoutResidualStateTest.kt`
- Modify: `tools/accessibility-harness.sh`
- Create: `tools/performance-harness.sh`
- Create: `tools/verify-release-artifacts.sh`
- Modify: `docs/mobile-pos/*.md` only when executable evidence justifies status
  or contract updates.

- [ ] **Step 1: Confirm the remaining hard gates**

Stop unless Backend Final staging evidence, the externally owned lost-response
gate, and approved numeric launch-p95, per-request-p95, UI-flow-p95, and
PSS-growth thresholds are available. Require all four correlated mutation
evidence records, the approved representative physical device, the externally
owned deterministic queued-closing evidence, and a readable non-empty external
release denylist. A production release claim additionally requires approved
production
application ID, canonical origin, public client ID, redirect URI, signing
fingerprint, OAuth allowlist, and `assetlinks.json` association. Do not reuse
debug or staging App Link evidence for production.

- [ ] **Step 2: Run complete unit and build verification**

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
```

Expected: every command exits 0.

- [ ] **Step 3: Run API 23 instrumentation**

```bash
./tools/run-device-tests.sh api23 --matrix
```

Expected: PASS on API 23.

- [ ] **Step 4: Run API 36 instrumentation**

```bash
./tools/run-device-tests.sh api36 --matrix
```

Expected: PASS on API 36.

- [ ] **Step 5: Run the repeatable low-end performance harness**

```bash
./tools/performance-harness.sh api23
./tools/performance-harness.sh --serial <approved-physical-device-serial>
```

Use the fixed low-end AVD, seed `20260724`, fixed timestamps and request IDs,
100-row read datasets, a 50-row cart, five warm-ups, 30 recorded samples, and 50
sale/receipt cycles. Preserve raw samples, summary JSON, instrumentation output,
logcat, commands, device metadata, and before/after meminfo. Fail on approved
threshold violations, retained destroyed views, main-thread StrictMode
violations, or out-of-memory behavior. Never report a metric with no approved
threshold as PASS.

Use a test-only runner, application, manifest, TLS fixture, and injected HTTPS
MockWebServer under `androidTest`; production trust and the production APK must
remain unchanged. The AVD is repeatable regression evidence. Final performance
acceptance uses the approved physical device and the same sampling protocol.
Annotate every performance setup/measurement method `SpecialHarnessOnly`.
`performance-harness.sh` invokes the exact
`MobilePosPerformanceTest#runPerformanceScenario` method and records its class,
method, TLS fixture fingerprint, and isolated artifact path. Broad device runs
must exclude the method.

- [ ] **Step 6: Verify accessibility and low-memory behavior**

```bash
./tools/accessibility-harness.sh api23
./tools/accessibility-harness.sh api36
```

Require complete PASS/FAIL evidence for labels/descriptions, 48 dp targets,
focus order, error announcements, non-color status, TalkBack, font scale 1.5,
portrait/landscape, external keyboard/scanner, process kill, and every core
screen. Use only synthetic data and apply the artifact redaction rules.

- [ ] **Step 7: Run staging lifecycle**

Exercise sign in, bootstrap, stale opening, opening, customer, scan/UOM, cart,
fully settled sale, lost-response replay, receipt, history, return, closing,
queued status, and logout. Record only non-sensitive request IDs and business
references.

After logout, `LogoutResidualStateTest` verifies no readable token, active OAuth
attempt, opening input, customer/catalog/history cache, cart, receipt, return or
closing input/status, encrypted terminal body, or protected navigation state
remains. It must not delete an unresolved mutation because logout is blocked in
that state.

Run both two-stage ADB process-death harnesses. Validate the contents and
correlation of the opening, sale, return, and closing evidence records: operation,
environment, backend SHA, original/replay UUID, post-upstream-completion drop,
original business reference, expected document type/name, document count exactly
one, operator evidence ID, and protocol reference. A single instrumentation
process, non-correlated ID, or MockWebServer response loss is not sufficient
evidence of server-commit recovery.

- [ ] **Step 8: Verify production App Links when making a production claim**

Install the production-signed artifact on API 36, run
`adb -s <serial> shell pm verify-app-links --re-verify <application-id>`, then
poll `adb -s <serial> shell pm get-app-links <application-id>` to a terminal
state with a bounded timeout. Assert only the exact production host and preserve
package, certificate, host, commands, and verification output. Stop if
production signing ownership or association is unavailable.

- [ ] **Step 9: Inspect release artifacts**

```bash
MOBILE_POS_RELEASE_DENYLIST=/absolute/external/path/denylist.txt \
  ./tools/verify-release-artifacts.sh app/build/outputs/apk/release/app-release.apk
```

The script fails for absent/unreadable/empty inputs, never prints or copies
denylist values, hashes the selected APK, inspects merged manifest/generated
configuration/unpacked APK/repository paths/captured release logs, and emits a
redacted report. Confirm no client secret, API key, shared credential, token,
verifier, authorization code, Administrator data, cleartext traffic, backup
path, test certificate/runner, or repository signing secret.

**Acceptance criteria:** All build, API 23/API 36 matrix, process-death,
accessibility, physical-device performance, four-mutation correlation, queued
closing, logout residual-state, production-conditional App Link, and release
security gates pass with complete redacted artifacts. No missing threshold or
external evidence is reported as PASS.

- [ ] **Step 10: Review the complete intended diff and stop**

Confirm only approved Android files changed and backend files did not change.
Report the proposed commit message `test: verify Android mobile POS lifecycle`.
Do not commit, publish, sign, or deploy without explicit approval.

## Audit Disposition

| Audit finding | Executable disposition or blocker |
| --- | --- |
| Cross-system proof covered sale only | Tasks 6, 9, 10, and 11 capture correlated exactly-one-document evidence; Task 12 validates all four records. |
| Site provisioning absent from Task 3 | Task 3 blocks on the approved configuration source, environment identity, test cashier, App Link evidence, and attempt lifetime. |
| Known-offline owner undefined | Task 5 creates and tests tri-state `ConnectivityStatus`. |
| Capability trigger matrix incomplete | Task 4 owns auth/profile/negative triggers; Tasks 6, 9, 10, and 11 own mutation/recovery triggers. |
| Unstable-network cases unassigned | Tasks 2 and 5 test every disconnect boundary and exact dispatch count. |
| Recovery retirement assertions missing | Task 5 tests serialize-once, stale process, key conflict, terminal persistence, acknowledgment, and restart. |
| Compile platform not deterministic | Task 1A records one source of truth; Task 1B tooling reads and verifies it. |
| Device-state matrix incomplete | Task 12 runs serial-pinned API 23/API 36 state matrices and accessibility harnesses. |
| Host-only tests could run broadly | Task 3 adds `SpecialHarnessOnly`; broad runners exclude it. |
| AVD claimed representative performance | Task 12 separates AVD regression evidence from approved physical-device final evidence. |
| Performance injection ownership missing | Task 12 owns test-only runner, application, manifest, TLS fixture, dependency, and injection changes. |
| Accessibility verification vague | Task 12 runs exact harness commands and a blocking per-screen checklist. |
| Release inspection not reproducible | Task 12 creates `verify-release-artifacts.sh` with external-denylist and redacted-report gates. |
| Tasks 6-11 used ambiguous verification shorthand | Every task now contains literal unit, release, lint, assemble, API 23, and API 36 commands. |
| Logout cache coverage incomplete | Tasks 4-11 extend `LogoutCoordinator`; Task 12 verifies no residual sensitive state. |
| Canonical-origin cases incomplete | Task 2 covers every normative parser invariant. |
| Mismatched callback retention untested | Task 3 preserves and tests the original pending attempt and expiry. |
| Task 3 red filter omitted API client | Task 3 runs `AuthenticatedMobilePosApiClientTest` explicitly. |
| Task 1B dependency evidence absent | Task 1B green gate runs `:app:dependencies`. |
| OAuth lifetime ambiguous | Task 3 hard-blocks until the 10-minute proposal or another value is approved. |
| Backend follow-up authorization ambiguous | A separate user request is mandatory before any backend documentation diff. |
| Fixture ownership incomplete | Task 2 defines provenance; each backend-gated feature refreshes/reviews its DTO and fixtures together. |
| Unknown enum compatibility untested | Task 2 owns the unknown-enum fixture and unsupported-state assertion. |
| OAuth conflicted with generic-method exclusion | Product/auth/API docs define only two literal canonical-origin OAuth control-plane routes. |
| Replay body definition ambiguous | Task 5 persists one serialized UTF-8 body plus format metadata and never reconstructs it. |
| WorkManager restoration ambiguous | Task 5 permits automatic work only for already persisted eligible mutations. |
| Blocked interfaces could be frozen early | Tasks 6, 8, 9, and 10 prohibit final DTO/signature decisions before their contract gates. |
| Retry and closing continuation ambiguous | Task 5 defines six-dispatch maximum and precedence; Task 11 uses explicit bounded status checks. |
| Serial requote and decimal behavior unresolved | They are explicit hard gates before affected feature tasks. |
| Queued staging trigger nondeterministic | Task 11 consumes an externally owned deterministic staging-only procedure. |

## Resolved Decision Gates

| Decision | Resolution | Scope |
| --- | --- | --- |
| Serial-change quote behavior | Approved 2026-08-04: serial identity remains local cart/quote authority only; serial changes invalidate and refresh an existing-field quote; backend validates serials at sale submission. | Task 8; no backend contract change |
| Non-serial cart quantity syntax | Approved 2026-08-04: ASCII decimal-dot, 0-6 fractional digits, `0 < qty <= 999999.999999`, exact parsing, no rounding or truncation. | Task 8 cart quantities only |

## Remaining Decision Gates

| Decision | Blocks |
| --- | --- |
| OAuth environment values, configuration source, test cashier, and attempt lifetime | Task 3 |
| Opening payment-mode projection | Task 6 |
| Decimal locale, precision, scale, bounds, and no-rounding behavior for future mutation inputs | Tasks 9, 10, and 11 |
| Authoritative payable and sale payment modes | Task 9 |
| Exact-only settlement alignment with backend overpayment examples | Task 9 |
| Remaining-returnable and authoritative refund workflow | Task 10 |
| External fault-gate owner, protocol, cleanup, and evidence schema | Tasks 6, 9, 10, 11, and 12 |
| Deterministic queued-closing staging procedure | Task 11 |
| Launch, request, UI-flow, and PSS thresholds | Task 12 |
| Representative physical low-end device | Task 12 |
| External release denylist | Task 12 |
| Production signing, App Links, and distribution | Production readiness |
| Backend factual cleanup and product/contract alignment | Separate backend request and review |

## Implementation Acceptance Criteria

- All product requirements and testing gates pass.
- Every mutation is idempotent across timeout and process death.
- API 23 and API 36 are verified.
- Jetpack Compose and Material 3 are used for current UI after Task 2B; Task 1B remains historical XML/ViewBinding evidence.
- OAuth PKCE S256 works without a client secret.
- No generic Frappe business API beyond the exact OAuth control-plane exception
  or local authoritative accounting exists.
- Accessibility and low-end performance evidence is preserved.
- Backend and Android diffs remain independent.
- Release verification does not claim R8 optimization or production deployment.
- Completion of one phase never authorizes the next phase.

## Execution Handoff

After this plan is approved and written, stop. A later explicit instruction must
choose either:

1. `superpowers:subagent-driven-development`, with a fresh implementer and
   review per task.
2. `superpowers:executing-plans`, with inline task checkpoints.

Neither option authorizes commits, later phases, publishing, signing, or
deployment without separate approval.
