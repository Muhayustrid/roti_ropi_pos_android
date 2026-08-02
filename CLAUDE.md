# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository rules

Read `AGENTS.md` before changing code. It is authoritative for security, API boundaries, change control, testing, and Android stack decisions. Communicate with the user in Indonesian; keep repository Markdown, code comments, test names, and commit messages in English. Do not commit, push, publish, deploy, or start a later implementation phase without explicit user approval.

When `.codegraph/` exists, use CodeGraph before broad repository searches or direct code reads. Query affected symbols and their callers before editing; use targeted reads or broad search only when CodeGraph cannot answer. CodeGraph is navigation only; source code, tests, Git state, and actual verification results remain authoritative.

## Model delegation policy

The main Fable agent acts as the orchestrator. It classifies each task, delegates to the minimum capable project agent, reviews delegated findings, and checks actual verification results before declaring completion.

### Routing

- Use `quick-explorer` for fast, narrow, read-only discovery.
- Use `routine-worker` as the default worker for approved routine implementation with clear scope and acceptance criteria.
- Use `complex-worker` for difficult, ambiguous, architectural, security, concurrency, data-consistency, recovery, contract, or other high-risk work.

### Escalation

- When scope is unknown, start with `quick-explorer`.
- When scope and acceptance criteria are clear, use `routine-worker`.
- Escalate to `complex-worker` when architectural ambiguity, security concerns, concurrency, unclear invariants, broad cross-layer impact, contract uncertainty, or repeated failure appears.
- Do not use Opus for simple exploration or mechanical edits.
- Do not allow Haiku to edit files or make high-impact decisions.
- The orchestrator remains responsible for synthesis, scope control, and completion claims.

### Efficiency

- Do not run Haiku, Sonnet, and Opus sequentially for every task; select only the minimum model necessary.
- Use parallel delegation only for independent read-only investigations or clearly separated workstreams.
- Avoid duplicate exploration by multiple agents or by the orchestrator after delegation.

### CodeGraph navigation

- Use CodeGraph before broad repository exploration to locate symbols, callers, callees, dependencies, execution paths, and affected tests.
- Verify load-bearing graph findings against actual source code and tests.
- Do not recursively scan or read unrelated files.

## Sequential Execution Policy

- Run all work sequentially in the current main agent; do not use `superpowers:subagent-driven-development`, `superpowers:dispatching-parallel-agents`, background or foreground Task subagents, agent teams, parallel agents, reviewer subagents, or overlapping independent audits.
- Superpowers may still be used in the main session for brainstorming, planning, TDD, systematic debugging, verification before completion, and finishing a development branch.
- If a skill requires spawning subagents, skip that skill and continue sequentially; if sequential execution is impossible, stop and ask the user.
- Require exactly one scoped final review by the current main agent.

## Commands

Run commands from the repository root. Gradle uses the wrapper; Android Studio's bundled JBR supplies the expected Java toolchain when `JAVA_HOME` is unset.

```bash
# Normal verification
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug

# Full debug/release gate
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease

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

AGP 9.2.1 does not expose `testReleaseUnitTest`; do not claim it passed. `local.properties` and local SDK paths are machine-specific.

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
