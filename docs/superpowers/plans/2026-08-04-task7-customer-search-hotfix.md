# Task 7 Customer Search Hotfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` for inline execution. Project authority forbids implementation or reviewer subagents for this hotfix.

**Goal:** Fix Task 7 audit findings B1-B4 and I1-I2 with deterministic concurrency, selection, pagination, open-search, and keyboard evidence.

**Architecture:** Keep customer networking in existing repository boundary. Make `CustomerSearchViewModel` one monitor-protected reducer whose network calls run outside the monitor and whose completion validation/publication is atomic. Route logout and selector-open events through explicit ViewModel operations, then prove behavior at unit, sheet, and production-root levels.

**Tech Stack:** Kotlin 2.2.10, coroutines/StateFlow, JUnit 4, Robolectric, Compose UI instrumentation, Android Gradle Plugin 9.2.1, API 23/API 36 harness.

## Global Constraints

- Work only in `/Users/rotiropi/DockerERPNext/POSERPNext-task7-hotfix`.
- Do not touch `/Users/rotiropi/DockerERPNext/POSERPNext-task8` or the read-only audit/backend repositories.
- Keep `minSdk 23`, existing Compose exception scope, `INSTRUMENTATION_TIMEOUT_SEC=180`, and API 24+ harness-package compilation.
- Add no dependency, automatic retry, hidden filtering, backend change, Task 8 work, or Task 9 work.
- Do not commit, push, create a PR, merge, or deploy.
- Run all verification serially.
- Final independent review happens in a separate read-only main-agent session after this implementation session stops.

## File Map

- `app/src/main/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchViewModel.kt`: serialized search authority, query lifecycle, page validation, selection semantics.
- `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`: exact customer/repository/profile/auth logout order.
- `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`: wire explicit customer logout operations.
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/Task4RootHost.kt`: dispatch selector-open blank search once through ViewModel no-op authority.
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchSheet.kt`: result selection callback and external-key focus order.
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchViewModelTest.kt`: deterministic B1-B4 tests.
- `app/src/test/java/com/rotiropi/pos_erpnext/session/LogoutCoordinatorTest.kt`: production cleanup ordering.
- `app/src/test/java/com/rotiropi/pos_erpnext/session/RecoveryLogoutCoordinatorTest.kt`: recovery-gated cleanup ordering.
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchSheetTest.kt`: selection transitions and keyboard journeys.
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchRootTest.kt`: production-root request, state, selection, and keyboard evidence.
- `docs/mobile-pos/implementation-plan.md`: factual Task 7 hotfix status and Task 8/9 status.
- `CLAUDE.md`: remove stale `testReleaseUnitTest` claim.

---

### Task 1: Serialize Customer Search Authority

**Interfaces:**
- Consumes: `CustomerSearchRequest`, `CustomerSearchResult`, `ApiCallCancellation`.
- Produces: monitor-protected `bind`, `onQueryChanged`, `loadMore`, `retry`, selection mutations, `invalidateAuthority`, `cancelActiveRequest`, `clearUi`, and `clear`.

- [ ] Add latch/barrier tests where completion pauses after authority validation while clear, logout, profile bind, or cashier bind attempts invalidation.
- [ ] Add a latch test where an explicit Retry completion supersedes an older non-cooperative request completion.
- [ ] Run `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew testDebugUnitTest --tests 'com.rotiropi.pos_erpnext.ui.customer.CustomerSearchViewModelTest'` and confirm new tests fail against split `isCurrent()`/publication.
- [ ] Replace independent mutable access with one private monitor. Keep `search(request, cancellation)` outside the monitor; completion enters once, validates identity/generation/query/offset/request ID, then publishes under the same monitor.
- [ ] Keep a test-only completion barrier invoked under the monitor immediately after validation, followed by a second validation before publication.
- [ ] Run the focused ViewModel class and confirm B1 tests pass.

### Task 2: Enforce Logout Ordering

**Interfaces:**
- Consumes: ViewModel operations from Task 1.
- Produces cleanup order `invalidate customer authority`, `cancel customer request`, `clear customer UI`, `clear repository`, `clear profile UI`, `clear authentication`.

- [ ] Change logout tests to record and assert all six operations in exact order, including recovery-gated and repeated logout paths.
- [ ] Run `./gradlew testDebugUnitTest --tests '*Logout*'` with SDK environment and confirm ordering tests fail.
- [ ] Split `LogoutCoordinator` customer cleanup callbacks and wire them from `MobilePosApplication` without changing recovery blocking.
- [ ] Run `./gradlew testDebugUnitTest --tests '*Logout*'` and the ViewModel class; confirm pass.

### Task 3: Correct Walk-In Selection

**Interfaces:**
- Consumes: bound `CustomerSearchIdentity.walkInCustomerId` and `CustomerRecord.isDefaultWalkIn`.
- Produces: `selectCustomer(customer)` choosing `WalkIn` only when both ID and backend marker match; otherwise `Registered`.

- [ ] Add ViewModel tests for profile default marked row, incorrectly marked other row, registered-to-walk-in, and walk-in-to-registered transitions.
- [ ] Add sheet and production-root tests for the same semantics, including display-name preservation only for walk-in and clearing on registered selection.
- [ ] Run ViewModel tests and `assembleDebugAndroidTest`; confirm new assertions fail.
- [ ] Route row activation through `selectCustomer`; preserve explicit profile walk-in action and optional display name.
- [ ] Run `./gradlew testDebugUnitTest --tests '*CustomerSearch*'` and `./gradlew assembleDebugAndroidTest`; confirm pass.

### Task 4: Reject Invalid Pagination Metadata Recoverably

**Interfaces:**
- Consumes: returned `CustomerSearchPage(start, limit, hasMore)` and requested authority offset.
- Produces: initial `error=Protocol`, page `pageError=Protocol`, retained failed authority for Retry, and no invalid success publication.

- [ ] Add production-consumed tests for initial/page `limit=0`, negative start, repeated offset, regressive offset, non-advancing metadata, and Retry after corrected metadata.
- [ ] Assert invalid initial response has no records and Retry; invalid page retains records and exact failed offset with page Retry; both set `loading=false` and prevent load loops.
- [ ] Run ViewModel tests and confirm failures.
- [ ] Validate metadata in atomic completion reducer before mutating records or next offset. Treat any invalid metadata as `CustomerSearchError.Protocol` through the existing recoverable initial/page paths.
- [ ] Run ViewModel and all `*CustomerSearch*` unit tests; confirm pass.

### Task 5: Make Identical Active Query a Strict No-Op

**Interfaces:**
- Consumes: trimmed query input.
- Produces: distinct `scheduledQuery`, `inFlightQuery`, failed initial authority, and completed-success query tracking.

- [ ] Add latch-based tests that wait until repository entry for active `"ayu"`, then send `"ayu "`; repeat for blank query and non-cooperative cancellation.
- [ ] Add explicit Retry-after-failure and query-change-then-return tests.
- [ ] Replace old debounce-restart test expectations with strict no-op expectations.
- [ ] Run ViewModel tests and confirm active duplicate tests fail.
- [ ] Make identical scheduled, active, failed, or completed-success normalized input return without cancellation, generation change, loading change, or new request. Only Retry may resend failed authority.
- [ ] Run ViewModel and all customer unit tests; confirm pass.

### Task 6: Search Blank Query on Selector Open

**Interfaces:**
- Consumes: `CustomerSearchViewModel.onSelectorOpened()`.
- Produces: one debounced `CustomerSearchRequest("", profile, 0, 20)` on first open and no duplicate after completed load or recomposition.

- [ ] Add production-root instrumentation asserting exact blank request, loading, and resulting empty/content state without typing.
- [ ] Add reopen and repeated visibility tests proving completed-query no-op.
- [ ] Build instrumentation and confirm tests fail because current open callback changes visibility only.
- [ ] Add `onSelectorOpened()` to feed current normalized query through reducer and call it before setting sheet visibility in `Task4RootHost`.
- [ ] Build instrumentation and run focused available instrumentation if device is already present.

### Task 7: Add External Keyboard Focus Coverage

**Interfaces:**
- Consumes: Compose focus traversal and key input.
- Produces: keyboard journey through search, rows, Retry, Load more, and Done with Enter activation.

- [ ] Add sheet instrumentation using `performKeyInput`, Tab/D-pad keys, focus assertions, and Enter activation; do not use direct `performClick()` for keyboard proof.
- [ ] Add production-root keyboard journey proving selected semantics and Done dismissal.
- [ ] Add explicit focus order/properties only where default traversal cannot satisfy required logical order.
- [ ] Retain and run existing touch, compact, landscape, and font-scale tests through official suites.

### Task 8: Correct Documentation

**Interfaces:**
- Produces factual tracked authority during review.

- [ ] Set Task 7 status to `Hotfix In Review` with B1-B4/I1-I2 scope.
- [ ] Keep Task 8 status factual for merged main authority and Task 9 `Not Started`; do not describe or modify separate Task 8 worktree.
- [ ] Replace stale `CLAUDE.md` claim that AGP lacks `testReleaseUnitTest` with current serial-gate guidance.
- [ ] Run `git diff --check`.

### Task 9: Focused Verification and Self-Review

- [ ] Run serially with SDK environment:

```bash
./gradlew testDebugUnitTest --tests 'com.rotiropi.pos_erpnext.ui.customer.CustomerSearchViewModelTest'
./gradlew testDebugUnitTest --tests '*CustomerSearch*'
./gradlew testDebugUnitTest --tests '*Logout*'
./gradlew assembleDebugAndroidTest
```

- [ ] Inspect intended diff, verify no Task 8/backend/audit changes, and fix every blocking or important self-review finding.
- [ ] Rerun affected focused commands after any source or test change.

### Task 10: Official Device Verification

- [ ] Run `./tools/run-device-tests.sh api23`; record exact test count and result.
- [ ] Confirm API 23 emulator exits before continuing.
- [ ] Run `./tools/run-device-tests.sh api36`; record exact test count and result.
- [ ] Confirm retained timeout and API 24+ harness-package behavior from diff.

### Task 11: Full Serial Gate and Handoff

- [ ] Run each command separately and serially:

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew assembleDebugAndroidTest
git diff --check
```

- [ ] Inspect `git diff`, `git status --short --branch`, changed-file list, test reports, and official device counts.
- [ ] Stop implementation session. Report results and request project owner to open separate read-only main-agent review session.
- [ ] Do not commit, push, create PR, merge, or deploy before verdict `APPROVE — no blocking findings`.
