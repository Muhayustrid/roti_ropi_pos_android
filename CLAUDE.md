# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository rules

Read `AGENTS.md` before changing code. It is authoritative for security, API boundaries, change control, testing, and Android stack decisions. Communicate with the user in Indonesian; keep repository Markdown, code comments, test names, and commit messages in English. Do not commit, push, publish, deploy, or start a later implementation phase without explicit user approval.

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