# Session Handoff: Task 4 Final State

Written 2026-08-02 after Task 4 implementation, review, final verification, and
the separately approved release-unit-test gate repair.

Task 4 is **Completed** and remains entirely uncommitted. No push, merge, deploy,
backend change, or Task 5 work occurred. The proposed commit message is
`feat: add scoped mobile POS bootstrap`; committing still requires explicit user
approval.

## Task status

| Task | Status |
| --- | --- |
| 1A–2E | Completed and merged before this worktree |
| 3 | Completed — commit `7a0aa51`, PR #7 |
| 4 | Completed — reviewed and verified, uncommitted in this worktree |
| 5–12 | Not Started; Task 5 requires separate explicit approval |

## What Task 4 delivers

- `MobilePosRepository` maps bootstrap DTOs to domain state, keeps sole in-memory
  capability ownership, auto-selects one profile, requires selection for multiple
  profiles, coalesces concurrent refreshes, and disables mutation capabilities
  until an authoritative refresh succeeds.
- Repository epochs discard pre-logout responses. `clear()` invalidates old
  in-flight work so stale success or failure cannot republish state, and the next
  refresh starts without an old `pos_profile`.
- `AppViewModel` and `ProfileSelectionViewModel` expose durable `StateFlow` state
  for authentication-triggered bootstrap, required Retry, profile selection,
  profile change, recreation, and route synchronization without observer/render/
  failure loops.
- First profile action wins while refresh is active. All profiles remain reachable
  through five-item native paging; profile rows and paging controls disable during
  refresh.
- `LogoutCoordinator` clears repository state, then profile UI state, then
  authentication. Task 5 still owns unresolved-mutation logout guarding.
- Task 4 visual state uses XML/ViewBinding through `Task4RootHost`,
  `Task4RootController`, `task4_root.xml`, and `profile_selection_screen.xml`.
  One `ComposeView` retains the unchanged legacy Task 3 sign-in/POS shell; its only
  Task 4 integration is an injectable ordered-logout callback.
- Native root state covers loading, profile selection, required-refresh Retry,
  exact `STALE_OPENING` warning, selected profile, profile change, and logout.
- Three reviewed bootstrap fixtures and manifest/DTO contract assertions cover one
  profile, multiple profiles, and stale opening.
- `gradle.properties` restores the required release unit-test component under AGP
  9.2.1 with `android.onlyEnableUnitTestForTheTestedBuildType=false`.

## Git state

- Worktree:
  `/Users/rotiropi/DockerERPNext/POSERPNext/.claude/worktrees/task-4-bootstrap-profile`
- Branch: `worktree-task-4-bootstrap-profile`
- Base and current `HEAD`: `99109a22cd6fb3e5cda702fcdae81bfd64832e14`
- `origin/main`: same SHA; ahead 0, behind 0 at handoff preparation.
- Index: no staged changes.
- Working tree: intentionally dirty with the complete uncommitted Task 4 diff and
  this handoff document.

The intended repository diff contains 30 paths: 9 modified existing paths and 21
new paths, including this handoff.

### Modified existing paths

- `app/build.gradle.kts`
- `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/MobilePosApplication.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/rotiropi/pos_erpnext/data/api/DtoContractTest.kt`
- `app/src/test/resources/api/v1/fixture-manifest.json`
- `docs/mobile-pos/implementation-plan.md`
- `gradle.properties`

### New production and layout paths

- `app/src/main/java/com/rotiropi/pos_erpnext/data/MobilePosRepository.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/session/LogoutCoordinator.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/AppViewModel.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/Task4RootController.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/Task4RootHost.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionScreen.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionViewModel.kt`
- `app/src/main/res/layout/profile_selection_screen.xml`
- `app/src/main/res/layout/task4_root.xml`

### New test and fixture paths

- `app/src/test/java/com/rotiropi/pos_erpnext/auth/ProductionAuthGraphTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/data/BootstrapRepositoryTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/session/LogoutCoordinatorTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/AppViewModelTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/Task4RootControllerTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/Task4RootLifecycleTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionScreenTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/profile/ProfileSelectionViewModelTest.kt`
- `app/src/test/resources/api/v1/bootstrap-multiple-profiles.json`
- `app/src/test/resources/api/v1/bootstrap-one-profile.json`
- `app/src/test/resources/api/v1/bootstrap-stale-opening.json`

### New handoff path

- `docs/mobile-pos/session-handoff-task-4.md`

The local SDD ledger at
`.superpowers/sdd/implementation-plan/progress.md` is git-ignored execution
scratch. It records RED/GREEN chronology and review/fix details but is not part of
the intended repository diff.

## TDD and focused verification

Task 4 production boundaries were introduced only after focused RED evidence for
missing repository, logout coordinator, ViewModels, routing, application graph,
native screen/controller/lifecycle behavior, and observable state. Focused GREEN
runs covered repository mapping and coalescing, profile selection/change/retry,
logout ordering, AppViewModel trigger counts, native root routing, application
graph wiring, DTO fixtures, and unchanged Task 3 shell state.

The combined focused suite passed:

```bash
./gradlew testDebugUnitTest \
  --tests '*Bootstrap*' \
  --tests '*ProfileSelection*' \
  --tests '*AppViewModelTest' \
  --tests '*LogoutCoordinatorTest' \
  --tests '*Task4*' \
  --tests '*ProductionAuthGraphTest' \
  --tests '*PosFoundationTest' \
  --tests '*DashboardProductsStateTest' \
  --tests '*ReportsMoreStateTest'
```

## Independent review

Initial independent Opus review returned `CHANGES_REQUIRED`, with no P0 and six
P1 findings. One fix round addressed all six:

1. Logout/bootstrap response race: epoch/discard protection.
2. Retry/recreation routing: durable profile state and repository synchronization.
3. Unreachable profiles: bounded native paging instead of truncation.
4. Concurrent taps: synchronized first-action-wins behavior.
5. Profile errors surviving logout: profile action invalidation and UI clearing.
6. Task 4 Compose visual routing: native XML/ViewBinding root restored while the
   legacy Compose shell remains bridged, not extended.

Scoped Opus re-review returned `APPROVE` with no open finding IDs. A separate
scoped Opus review of the release-unit-test gate repair also returned `APPROVE`,
with no P0, P1, or lower-severity findings.

## Final verification evidence

The exact-once Task 4 gate ran sequentially on 2026-08-02:

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` | PASS — 187/187, 0 failures, 0 errors, 0 skipped |
| `./gradlew testReleaseUnitTest` | FAIL exit 1 — task absent; not rerun inside exact-once gate |
| `./gradlew lintDebug` | PASS |
| `./gradlew lintRelease` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assembleRelease` | PASS |
| `./tools/run-device-tests.sh api23` | PASS — 75/75 |
| `./tools/run-device-tests.sh api36` | PASS — 75/75 |

The failure was gate infrastructure, not a test failure. AGP 9 changed
`android.onlyEnableUnitTestForTheTestedBuildType` from `false` to `true`, so the
project created only the tested debug unit-test component. A diagnostic command
with the property set to `false` exposed `testReleaseUnitTest`; the separately
approved one-line project configuration repair made that behavior durable.

Fresh corrected follow-up:

```text
./gradlew testReleaseUnitTest
BUILD SUCCESSFUL
26 suites, 187 tests, 0 failures, 0 errors, 0 skipped
```

This preserves the exact-once gate history while closing its only blocker.
`git diff --check` also passed after the repair.

## Known limits and non-goals

- Task 4 adds no offline mutation queue, WorkManager recovery, pending-mutation
  persistence, or unresolved-mutation logout guard. Those belong to Task 5.
- Capability values remain UI availability only; ERPNext remains authoritative.
- `cancel_sale` and uncontracted features remain unavailable.
- No backend source, backend contract, staging configuration, OAuth provisioning,
  production signing, or deployment changed.
- Robolectric 4.14 cannot model API 36 (`UnknownSdk`), so API 36 UI confidence comes
  from the required 75/75 device suite, not a fabricated host-SDK result.
- Release unit-test compilation emitted non-fatal deprecation and always-true test
  warnings. Tests, lint, and assembly still passed; independent review opened no
  finding for them.

## What to read, in order

1. `AGENTS.md` — binding stack, security, language, and approval rules.
2. `docs/mobile-pos/implementation-plan.md:31-52` — verified task table and next
   approval boundary.
3. `docs/mobile-pos/implementation-plan.md:1027-1141` — Task 4 evidence and Task 5
   dependency gate.
4. This file — final Task 4 worktree, diff, review, and verification state.
5. `.superpowers/sdd/implementation-plan/progress.md` — local RED/GREEN execution
   ledger when resuming in this same worktree.

## Exact stopping and resume point

Stop here. Task 4 is complete but uncommitted. Next authorized action is user
review of the intended diff. Only explicit approval may authorize a commit using
`feat: add scoped mobile POS bootstrap` or authorize separate Task 5 work.
Approval to commit Task 4 does not authorize push, merge, deploy, or Task 5.

Before any later action, inspect without mutating:

```bash
git diff --check
git status --short
git diff -- app gradle.properties docs/mobile-pos/implementation-plan.md \
  docs/mobile-pos/session-handoff-task-4.md
git ls-files --others --exclude-standard
```
