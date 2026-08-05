# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository rules

Read `AGENTS.md` before changing code. It is authoritative for security, API boundaries, change control, testing, and Android stack decisions. Communicate with the user in Indonesian; keep repository Markdown, code comments, test names, and commit messages in English. Do not commit, push, publish, deploy, or start a later implementation phase without explicit user approval.

When `.codegraph/` exists, use CodeGraph before broad repository searches or direct code reads. Query affected symbols and their callers before editing; use targeted reads or broad search only when CodeGraph cannot answer. CodeGraph is navigation only; source code, tests, Git state, and actual verification results remain authoritative.

## Model delegation policy

The current primary agent owns orchestration, scope control, integration, verification, and completion claims. It may execute work directly or delegate only when delegation materially improves speed, isolation, expertise, or review quality.

### Routing

- Use direct execution for narrow, well-understood work.
- Use `quick-explorer` for fast, narrow, read-only discovery.
- Use `routine-worker` for approved routine implementation with clear scope and acceptance criteria.
- Use `complex-worker` for difficult, ambiguous, architectural, security, concurrency, data-consistency, recovery, contract, or other high-risk work.

### Escalation

- When scope is unknown, start with targeted CodeGraph navigation or one `quick-explorer`.
- When scope and acceptance criteria are clear, use direct execution or `routine-worker`.
- Escalate to `complex-worker` when architectural ambiguity, security concerns, concurrency, unclear invariants, broad cross-layer impact, contract uncertainty, or repeated failure appears.
- Do not use a heavy model for simple exploration or mechanical edits.
- Do not allow a lightweight read-only agent to edit files or make high-impact decisions.
- The primary agent remains responsible for synthesis, scope control, integration, and completion claims.

### Efficiency

- Delegation is optional, not mandatory.
- Default to direct execution or one bounded subagent.
- Without explicit user approval, use at most three subagents for one logical task.
- Do not run lightweight, medium, and heavy models sequentially for every task.
- Avoid duplicate exploration by multiple agents or by the primary agent after delegation.
- Do not create recursive agent teams or allow subagents to spawn additional subagents.
- Larger agent teams require explicit user approval and a written justification.

### CodeGraph navigation

- Use CodeGraph before broad repository exploration to locate symbols, callers, callees, dependencies, execution paths, and affected tests.
- Verify load-bearing graph findings against actual source code and tests.
- Do not recursively scan or read unrelated files.

## Subagent Execution Policy

- Subagents, agent teams, and applicable Superpowers delegation skills are allowed only when delegation materially improves speed, isolation, expertise, or review quality.
- Keep delegated tasks independent, bounded, and explicit about read and write scope.
- Use at most one active writing agent per Git worktree.
- Do not assign overlapping file edits, shared state owners, or the same acceptance criteria to multiple writing agents.
- Use parallel agents only for independent read-only investigations or clearly separated implementation workstreams in separate worktrees.
- Otherwise, run delegated work sequentially.
- Read-only reviewers must not run concurrently with an active writer on the same worktree.
- The primary agent must review delegated findings and diffs, integrate changes, run required verification, and perform one scoped self-review before declaring implementation ready for final review.
- For high-risk work, a separate independent read-only review session may be used after the implementation session stops and the source no longer changes. This separate session is not a reviewer subagent of the implementation session.
- Do not run repeated review loops unless the previous review found blocking defects or the implementation changed afterward.

## Commands

Run commands from the repository root. Gradle uses the wrapper; Android Studio's bundled JBR supplies the expected Java toolchain when `JAVA_HOME` is unset.

```bash
# Normal verification
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug

# Full debug/release gate
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease

# One unit-test class or package
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClientTest"
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.auth.*"

# Connected device already available
./gradlew connectedDebugAndroidTest

# Managed API-level device runs; creates reports under app/build/reports/mobile-pos-devices/
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36

# Build app and instrumentation APKs without running a device
./gradlew assembleDebug assembleDebugAndroidTest
```

Verify the current Gradle task graph before claiming that a task exists or does not exist. In the current verified project state, `testReleaseUnitTest` is available and belongs in the full serial gate. Run final verification commands serially unless independence has been proven. `local.properties` and local SDK paths are machine-specific.

## Architecture

This is one Android application module, `:app`, using Kotlin, `minSdk 23`, and `targetSdk 36`. `MobilePosApplication` is the manual process-wide dependency container: it wires OAuth/token storage, authenticated OkHttp transport, `MobilePosRepository`, ViewModels, and logout coordination. No DI framework, Retrofit, ORM, or WorkManager is currently used.

`MainActivity` inflates the XML/ViewBinding root and delegates orchestration to `Task4RootHost`. That host combines authentication, repository, and profile-selection state and embeds the existing Compose Material 3 `PosShell`. This is a hybrid UI: preserve existing behavior, but follow `AGENTS.md` and obtain explicit approval before adding or extending Compose UI.

Core boundaries:

- `auth/`: AppAuth OAuth 2.0 Authorization Code with PKCE S256. Tokens and pending attempts use Android Keystore-backed storage. Client is public; no client secret is allowed.
- `data/api/`: kotlinx.serialization DTOs and `AuthenticatedMobilePosApiClient`. Requests must use the exact canonical HTTPS origin and only endpoints enumerated by `MobilePosEndpoint`.
- `data/MobilePosRepository.kt`: sole in-memory owner of bootstrap, profile, opening-session, and capability state. It maps wire DTOs to immutable domain models and coalesces concurrent refreshes.
- `ui/`: ViewModels expose state consumed by XML/ViewBinding and Compose screens. `Task4RootHost` is the lifecycle-aware bridge.
- `session/LogoutCoordinator.kt`: clears sensitive local state and blocks unresolved logout transitions.

Only the versioned Mobile POS backend API under `/api/method/roti_ropi_pos.api.v1...` is valid. Never call generic Frappe resource APIs, arbitrary whitelisted methods, ERPNext document-save APIs, or core POS helpers directly. ERPNext remains authoritative for pricing, tax, stock, totals, payment settlement, and accounting decisions. Contract fixtures live in `app/src/test/resources/api/v1/endpoint-contracts.json`; update implementation and contract tests together when approved backend contracts change.

## Source sets and test harnesses

Debug-only demo fixtures live under `app/src/debug/`; release uses an unsupported stub under `app/src/release/` so demo data cannot ship. Keep this separation intact.

Instrumentation members annotated `SpecialHarnessOnly` are invoked by host scripts for process-death scenarios and excluded from broad suites. Do not remove or broadly execute them without checking `tools/run-device-tests.sh` and `tools/oauth-process-death.sh`.

Android-side plans and verification records live in `docs/mobile-pos/`. Backend contracts are maintained in the separate backend repository referenced by `AGENTS.md`; review Android and backend diffs independently.
