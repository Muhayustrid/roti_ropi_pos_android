# Task 1B Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Task 1B by restoring executable instrumentation tests and producing deterministic API 23/API 36 XML/ViewBinding device evidence.

**Architecture:** Keep the existing XML/ViewBinding application shell unchanged. Align only the AndroidX Test dependency set responsible for the instrumentation runtime failure, then harden the two existing host scripts around exact SDK packages, fixed AVD identities, serial-pinned ADB operations, bounded readiness checks, and durable evidence artifacts.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, XML Views, ViewBinding, AndroidX Test, Espresso, Bash, Gradle Wrapper, Android SDK command-line tools, ADB, API 23, API 36, targetSdk 36, compileSdk 36.1.

## Global Constraints

- Work only in `/Users/rotiropi/DockerERPNext/POSERPNext`.
- Preserve `minSdk 23`, `targetSdk 36`, and compile platform 36.1.
- Keep XML Views and ViewBinding; add no Compose artifact.
- Do not change application UI behavior, API transport, backend contracts, or Task 2.
- Preserve all unrelated local changes.
- Install approved SDK tools/images only under `~/Library/Android/sdk`.
- Do not weaken, skip, or delete existing instrumentation assertions.
- Use exact API 23/API 36 Google APIs images for host ABI; never fall back to another API, ABI, AVD, or ambient device.
- Perform no commit or push.

## File Map

| Path | Responsibility |
| --- | --- |
| `gradle/libs.versions.toml` | Coherent AndroidX Test and Espresso versions. |
| `app/build.gradle.kts` | Minimal test dependency wiring only. |
| `app/src/androidTest/java/com/rotiropi/pos_erpnext/ExampleInstrumentedTest.kt` | Visible XML sign-in assertion; preserve behavior. |
| `app/src/androidTest/java/com/rotiropi/pos_erpnext/ViewBindingLifecycleTest.kt` | ViewBinding destruction/recreation assertions; preserve behavior. |
| `tools/create-test-avds.sh` | Exact SDK/image validation, fixed AVD creation, deterministic config, package revision evidence. |
| `tools/run-device-tests.sh` | Exact AVD selection, serial-pinned setup/install/instrumentation, failure classification, evidence capture. |
| `docs/mobile-pos/implementation-plan.md` | Task 1B status and fresh verification evidence after all gates pass. |

---

### Task 1: Align AndroidX Test Runtime

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify only if required: `app/build.gradle.kts`
- Test without weakening: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ExampleInstrumentedTest.kt`
- Test without weakening: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ViewBindingLifecycleTest.kt`

**Interfaces:**
- Consumes: Existing `AndroidJUnitRunner`, Espresso `onView`, `FragmentScenario`, and three instrumentation tests.
- Produces: One resolved AndroidX Test dependency graph with no duplicate incompatible `androidx.test` runtime classes.

- [ ] **Step 1: Capture current dependency conflict**

Run:

```bash
./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath \
  > /tmp/task-1b-debugAndroidTestRuntimeClasspath-before.txt
./gradlew :app:dependencyInsight \
  --configuration debugAndroidTestRuntimeClasspath \
  --dependency androidx.test \
  > /tmp/task-1b-androidx-test-insight-before.txt
```

Expected: output identifies the resolved AndroidX Test core/runner/monitor/espresso artifacts behind the observed `ReflectiveMethod` `NoSuchMethodError`.

- [ ] **Step 2: Preserve the red runtime evidence**

Run on the available emulator before dependency edits:

```bash
./gradlew connectedDebugAndroidTest
```

Expected: FAIL at `launch_displays_xml_sign_in_destination` with the existing `NoSuchMethodError`. If failure changes, record the exact new failure and diagnose before editing.

- [ ] **Step 3: Apply the smallest coherent version alignment**

Update the version catalog so AndroidX Test JUnit and Espresso resolve against a compatible release family. Add an explicit test core/runner dependency only when `dependencyInsight` proves a transitive mismatch remains. Do not add production dependencies or test exclusions that hide classes.

Use this coherent release set and separate Fragment testing manifest from test runtime code:

```toml
[versions]
androidxTestCore = "1.7.0"
androidxTestJunit = "1.3.0"
espressoCore = "3.7.0"
androidxTestRunner = "1.7.0"

[libraries]
androidx-fragment-testing-manifest = { group = "androidx.fragment", name = "fragment-testing-manifest", version.ref = "fragmentKtx" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
```

Wire them as:

```kotlin
debugImplementation(libs.androidx.fragment.testing.manifest)
androidTestImplementation(libs.androidx.fragment.testing)
androidTestImplementation(libs.androidx.test.core)
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.androidx.junit)
```

Remove `debugImplementation(libs.androidx.fragment.testing)`: it currently packages `androidx.test:core:1.5.0` into the app APK while Espresso 3.7.0 and JUnit 1.3.0 request Test Core 1.7.0 in the test APK, producing the observed runtime class mismatch. Keep all existing test bodies unchanged unless compilation requires an API rename; preserve each assertion.

- [ ] **Step 4: Verify instrumentation compilation**

Run:

```bash
./gradlew assembleDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 5: Verify green runtime on available emulator**

Run:

```bash
./gradlew connectedDebugAndroidTest
```

Expected: all three existing tests execute and PASS; no process crash, skipped test, `NoSuchMethodError`, or incomplete run.

- [ ] **Step 6: Reinspect resolved graph**

Run:

```bash
./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath \
  > /tmp/task-1b-debugAndroidTestRuntimeClasspath-after.txt
./gradlew :app:dependencyInsight \
  --configuration debugAndroidTestRuntimeClasspath \
  --dependency androidx.test \
  > /tmp/task-1b-androidx-test-insight-after.txt
```

Expected: one coherent resolved family; no conflicting forced downgrade or duplicate runtime implementation.

- [ ] **Step 7: Review Task 1 diff**

Run:

```bash
git diff --check -- gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest
git diff -- gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest
```

Expected: dependency alignment only; existing assertions remain intact.

---

### Task 2: Install Exact Android SDK Requirements

**Files:**
- No repository files.
- Local SDK only: `~/Library/Android/sdk`.

**Interfaces:**
- Consumes: Android CLI `android sdk`, host ABI, compile platform 36.1.
- Produces: `avdmanager`, emulator, platform-tools, platform 36.1, and exact API 23/API 36 Google APIs images.

- [ ] **Step 1: Inspect installed packages and host ABI**

Run:

```bash
uname -m
android info
android sdk list --all
```

Expected: host ABI is recorded; missing command-line tools and images are confirmed.

- [ ] **Step 2: Install command-line tools and exact images**

Use Android CLI package names from `android sdk list --all`. Install:

```text
cmdline-tools;latest
platform-tools
emulator
platforms;android-36.1
system-images;android-23;google_apis;arm64-v8a
system-images;android-36;google_apis;arm64-v8a
```

Run using exact `android sdk install` syntax shown by `android help sdk` and package listing. Do not install another API/ABI as fallback.

Expected: installation succeeds under `~/Library/Android/sdk`.

- [ ] **Step 3: Verify tools and packages**

Run:

```bash
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager list target
~/Library/Android/sdk/emulator/emulator -version
~/Library/Android/sdk/platform-tools/adb version
android sdk list --all
```

Expected: exact packages and tools are available; record package revisions.

---

### Task 3: Make AVD Creation Deterministic

**Files:**
- Modify: `tools/create-test-avds.sh`

**Interfaces:**
- Consumes: `app/build.gradle.kts`, exact SDK package directories, `avdmanager`, emulator binary, host ABI.
- Produces: fixed AVDs `mobile-pos-api23`, `mobile-pos-api36` plus `app/build/reports/mobile-pos-devices/avd-metadata.txt`.

- [ ] **Step 1: Add a read-only script self-check mode**

Add `--check` to validate prerequisites and print derived values without creating/removing AVDs. It must output:

```text
compile_platform=36.1
host_abi=<arm64-v8a|x86_64>
api23_package=system-images;android-23;google_apis;<abi>
api36_package=system-images;android-36;google_apis;<abi>
```

Unknown arguments must exit nonzero with usage.

- [ ] **Step 2: Run the self-check red assertion**

Run before implementing parsing:

```bash
./tools/create-test-avds.sh --check
```

Expected before the change: FAIL because `--check` is unsupported or compile SDK prints `36` instead of `36.1`.

- [ ] **Step 3: Implement exact compile-platform parsing**

Parse both Gradle declarations:

```kotlin
version = release(36) {
    minorApiLevel = 1
}
```

Produce `36.1`; fail when either release or minor value cannot be determined. Verify `platforms;android-36.1` exists. Do not default silently to `36`.

- [ ] **Step 4: Validate exact package metadata**

Use SDK package metadata (`package.xml` or `source.properties`) to confirm API, tag, ABI, and revision for both images. Resolve `avdmanager` only from the approved SDK root or PATH. Record:

```text
compile_platform
host_abi
avdmanager_path
emulator_version
api23_package
api23_revision
api36_package
api36_revision
```

Write metadata atomically to `app/build/reports/mobile-pos-devices/avd-metadata.txt`.

- [ ] **Step 5: Create fixed AVDs and replace config keys idempotently**

Create only:

```text
mobile-pos-api23
mobile-pos-api36
```

Use exact package identifiers. Replace existing config keys instead of appending duplicates. Configure fixed CPU, RAM, width, height, density, keyboard, locale, timezone, snapshot-disabled, and cold/wipe policy metadata. Re-running the script must yield one value per key.

- [ ] **Step 6: Verify self-check and idempotent creation**

Run:

```bash
./tools/create-test-avds.sh --check
./tools/create-test-avds.sh
./tools/create-test-avds.sh
android emulator list
```

Expected: both AVD names appear exactly once; config keys are not duplicated; metadata records exact revisions and emulator version.

- [ ] **Step 7: Review Task 3 diff**

Run:

```bash
bash -n tools/create-test-avds.sh
git diff --check -- tools/create-test-avds.sh
git diff -- tools/create-test-avds.sh
```

Expected: PASS; no unrelated script or source change.

---

### Task 4: Make Device Runs Exact and Evidenced

**Files:**
- Modify: `tools/run-device-tests.sh`

**Interfaces:**
- Consumes: target `api23|api36`, optional `--matrix`, fixed AVD names, app/test APK outputs.
- Produces: serial-pinned instrumentation result and evidence directories `app/build/reports/mobile-pos-devices/api23/` or `app/build/reports/mobile-pos-devices/api36/`.

- [ ] **Step 1: Define CLI validation checks**

Required behavior:

```bash
./tools/run-device-tests.sh                 # exit nonzero
./tools/run-device-tests.sh api24           # exit nonzero
./tools/run-device-tests.sh api23 --bad     # exit nonzero
./tools/run-device-tests.sh api23 --matrix  # accepted
```

`--matrix` enables additional orientation/font-scale/locale state runs only if the current Task 1B plan defines them; otherwise it must be rejected rather than ignored. For Task 1B normal commands, `api23` and `api36` remain sufficient.

- [ ] **Step 2: Run red CLI checks**

Run:

```bash
./tools/run-device-tests.sh api23 --bad
```

Expected before the change: current script incorrectly accepts or ignores the extra argument; record result.

- [ ] **Step 3: Implement exact AVD and serial selection**

For each serial returned by `adb devices`, query:

```bash
adb -s "$SERIAL" emu avd name
adb -s "$SERIAL" shell getprop ro.build.version.sdk
```

Reuse only when both AVD name and API equal target. When starting, capture the new serial rather than selecting first `emulator-*`. Fail if identity/API mismatch or ambiguous serial appears.

- [ ] **Step 4: Implement bounded readiness**

Wait with explicit deadlines for:

```text
adb device state = device
sys.boot_completed = 1
package manager responds to `pm path android`
home activity can resolve
```

On timeout, print target, serial, last observed state, and emulator log path; exit nonzero.

- [ ] **Step 5: Apply deterministic runtime state**

Pin all commands with `-s "$RUNNING_SERIAL"`. Apply and verify:

```text
locale: en-US
font scale: 1.0
timezone: UTC
window_animation_scale: 0
transition_animation_scale: 0
animator_duration_scale: 0
orientation: portrait
```

If API 23 uses a different supported mechanism, branch by runtime API. Fail when required state cannot be verified.

- [ ] **Step 6: Build, install, and run complete instrumentation**

Run Gradle with exact tasks:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
```

Install both APKs with pinned serial. Run:

```bash
adb -s "$RUNNING_SERIAL" shell am instrument -w -r \
  com.rotiropi.pos_erpnext.test/androidx.test.runner.AndroidJUnitRunner
```

Require:

```text
INSTRUMENTATION_CODE: -1
OK (3 tests)
```

Fail on `FAILURES!!!`, `Process crashed`, missing completion markers, nonzero host command, or any test count other than three.

- [ ] **Step 7: Capture evidence**

Write under `app/build/reports/mobile-pos-devices/api23/` or `app/build/reports/mobile-pos-devices/api36/`:

```text
commands.txt
versions.txt
device_properties.txt
runtime_state.txt
instrumentation.txt
emulator.log (when started)
report-copy/ or junit XML when generated
```

Record target, AVD, serial, runtime API, package revisions, emulator/ADB/Gradle versions, APK hashes, start/end state, and exact commands without credentials.

- [ ] **Step 8: Stop only owned emulator**

Use a trap. Stop only when the runner started that exact serial. Preserve failure artifacts before stopping. Never kill a pre-existing emulator.

- [ ] **Step 9: Verify shell and CLI behavior**

Run:

```bash
bash -n tools/run-device-tests.sh
./tools/run-device-tests.sh api23 --bad && exit 1 || true
git diff --check -- tools/run-device-tests.sh
git diff -- tools/run-device-tests.sh
```

Expected: syntax and diff checks pass; invalid option rejected.

---

### Task 5: Run Full Task 1B Gate

**Files:**
- No implementation edits during this task unless a command exposes a Task 1B defect; any fix returns to its owning task and reruns that task's red/green cycle.

**Interfaces:**
- Consumes: coherent test dependencies, exact AVDs, deterministic runner.
- Produces: fresh complete Task 1B evidence.

- [ ] **Step 1: Run clean Gradle verification**

Run:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew :app:dependencies
```

Expected: every available command exits 0. Dependency output contains no `androidx.compose` artifact.

- [ ] **Step 2: Run API 23 matrix**

Run:

```bash
./tools/run-device-tests.sh api23
```

Expected: exact `mobile-pos-api23`, runtime API 23, all three tests PASS, complete evidence directory.

- [ ] **Step 3: Run API 36 matrix**

Run:

```bash
./tools/run-device-tests.sh api36
```

Expected: exact `mobile-pos-api36`, runtime API 36, all three tests PASS, complete evidence directory.

- [ ] **Step 4: Inspect runtime UI evidence**

If instrumentation output alone does not contain visible sign-in proof, start the target AVD through the runner's supported mechanism, set `RUNNING_SERIAL` from the runner evidence, deploy the debug APK, then run:

```bash
android layout --device="$RUNNING_SERIAL" --pretty
android screen capture --device="$RUNNING_SERIAL" \
  --output="app/build/reports/mobile-pos-devices/$TARGET_API/sign-in.png"
```

Expected: hierarchy contains text `Sign in` with resource ID `tvSignInTitle`.

- [ ] **Step 5: Inspect security configuration and Compose absence**

Run:

```bash
grep -n 'allowBackup="false"\|usesCleartextTraffic="false"' app/src/main/AndroidManifest.xml
grep -RIn 'androidx.compose\|org.jetbrains.kotlin.plugin.compose' \
  app build.gradle.kts gradle/libs.versions.toml || true
```

Expected: backup and cleartext controls present; Compose search returns no application/configuration match.

---

### Task 6: Synchronize Task 1B Status

**Files:**
- Modify: `docs/mobile-pos/implementation-plan.md`

**Interfaces:**
- Consumes: all successful Task 5 command output and device evidence.
- Produces: Task 1B status `Completed` with exact fresh evidence and next incomplete task Task 2.

- [ ] **Step 1: Update status only after all gates pass**

Change the status table and Task 1B section from `In Progress` to `Completed`. Replace stale failure text with exact successful command summaries, API levels, test count, and evidence paths. Do not change Task 2 acceptance criteria or mark Task 2 complete.

- [ ] **Step 2: Run final documentation and repository checks**

Run:

```bash
git diff --check
git diff -- gradle/libs.versions.toml app/build.gradle.kts \
  app/src/androidTest tools/create-test-avds.sh tools/run-device-tests.sh \
  docs/mobile-pos/implementation-plan.md docs/mobile-pos/task-1b-completion-design.md \
  docs/mobile-pos/task-1b-completion-plan.md
git status --short
```

Expected: only approved Task 1B files plus pre-existing unrelated local changes appear.

- [ ] **Step 3: Report and stop**

Report in Bahasa Indonesia:

- changed files;
- dependency root cause and exact alignment;
- exact SDK packages installed;
- all Gradle/device commands and actual results;
- API 23/API 36 instrumentation counts;
- evidence paths;
- remaining risks;
- diff/status summary;
- explicit no-commit/no-push confirmation.

Do not begin Task 2.
