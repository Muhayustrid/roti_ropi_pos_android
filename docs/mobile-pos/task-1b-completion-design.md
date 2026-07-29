# Task 1B Completion Design

## Goal

Complete Task 1B without changing application behavior or starting Task 2. Preserve the XML/ViewBinding shell, make its instrumentation tests executable, and produce deterministic API 23 and API 36 device evidence.

## Scope

Modify only the minimum Task 1B build/test dependencies, `tools/create-test-avds.sh`, `tools/run-device-tests.sh`, and Task 1B status evidence in `docs/mobile-pos/implementation-plan.md`. Change tests only if dependency alignment does not resolve the existing runner failure. Do not modify Mobile POS API transport, backend contracts, authentication, or feature UI.

## Instrumentation Compatibility

Inspect the resolved AndroidX Test dependency graph behind the `ReflectiveMethod` `NoSuchMethodError`. Align the smallest set of AndroidX Test and Espresso versions that supports API 23 and target API 36. Keep the existing launch and ViewBinding lifecycle assertions intact; do not suppress, skip, or weaken them.

## AVD Creation

`tools/create-test-avds.sh` will:

- derive compile platform `36.1` from Gradle configuration;
- require the exact installed compile platform;
- require exact Google APIs API 23 and API 36 images for the host ABI;
- create fixed AVD names `mobile-pos-api23` and `mobile-pos-api36`;
- apply fixed CPU, RAM, display, density, locale, timezone, animation, wipe, and snapshot policy;
- record Android SDK package revisions and emulator version;
- fail instead of choosing another platform, ABI, image, or device.

## Device Runner

`tools/run-device-tests.sh` will accept exactly `api23` or `api36` plus optional `--matrix`. It will start or reuse only the requested AVD, pin its serial, verify AVD identity and runtime API, wait with bounded timeouts for boot and package-manager readiness, apply deterministic runtime settings, build and install app/test APKs, and run the complete instrumentation suite.

The runner will preserve command metadata, tool versions, image/package revisions, device properties, instrumentation output, and available JUnit/report artifacts. It will fail on a wrong device, timeout, install failure, process crash, incomplete run, failed test, or missing expected test count. It will stop only an emulator it started.

## Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew lintRelease
./gradlew assembleRelease
./gradlew :app:dependencies
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
git diff --check
git status --short
```

Confirm no Compose artifact exists. Inspect layout hierarchy or screenshot only if instrumentation results do not clearly prove the visible sign-in destination. Task 1B becomes `Completed` only when all required Gradle and API 23/API 36 checks pass.

## Environment Setup

The user approved installation of Android SDK command-line tools and exact API 23/API 36 system images under `~/Library/Android/sdk`. Installation changes the local SDK only, not repository source.

## Constraints

- Preserve unrelated local changes.
- Do not implement or refactor Task 2.
- Do not weaken security, accessibility, or lifecycle assertions.
- Do not commit or push.
