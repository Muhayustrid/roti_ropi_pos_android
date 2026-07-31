# Task 2B Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this verification plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete fresh, blocking verification for existing uncommitted Task 2B Compose foundation without starting Task 2C.

**Architecture:** Verify current implementation before editing it. Render each existing debug preview through installed Android Studio Quail, inspect generated images and semantics, run available Gradle and exact API 23/API 36 gates, then review only Task 2B diff. Modify implementation only after a reproducible failing check identifies a source defect.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Compose BOM 2026.06.00, Material 3, Navigation Compose, JUnit 4, Compose UI test, Android Studio Quail 1 | 2026.1.1 Patch 2, API 23 and API 36 AVDs.

## Global Constraints

- Preserve all pre-existing tracked and untracked work.
- Do not start Task 2C.
- Do not change backend code or contracts.
- Do not add dependencies, abstractions, screenshot frameworks, or permanent Task 12 harnesses.
- Do not run `git commit`, `git push`, merge, rebase, create a pull request, or deploy.
- Treat unavailable required evidence or any failed required command as a Task 2B blocker.
- Keep release runtime free of synthetic ERPNext data and unsupported feature claims.
- Use project name `POS-ERPNext`, not filesystem path, for `android studio render-compose-preview --project`.
- AGP 9.2.1 exposes no `testReleaseUnitTest`; record that fact, run release Kotlin compilation plus release assembly, and never claim the missing task passed.

---

### Task 1: Capture Compose Preview Matrix

**Files:**
- Read: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt`
- Generate: `app/build/reports/mobile-pos-task2b/previews/*.png`
- Generate: `app/build/reports/mobile-pos-task2b/previews/*.semantics.txt`

**Interfaces:**
- Consumes: five existing `@Preview` composables in `FoundationPreviews.kt`.
- Produces: one image and one semantics output per preview.

- [ ] **Step 1: Resolve active Android Studio instance**

Run:

```bash
android studio check
```

Expected: one `READY` project named `POS-ERPNext`; record its PID.

- [ ] **Step 2: Render phone light blue preview**

Run from repository root, substituting the PID from Step 1:

```bash
mkdir -p app/build/reports/mobile-pos-task2b/previews
android studio render-compose-preview \
  --project POS-ERPNext \
  --pid <PID> \
  --output-image-file=app/build/reports/mobile-pos-task2b/previews/phone-light-blue.png \
  --print-semantics \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt \
  FoundationShellPreview \
  > app/build/reports/mobile-pos-task2b/previews/phone-light-blue.semantics.txt
```

Expected: exit 0, non-empty PNG, semantics containing `Home`, `Products`, `Cashier`, `Reports`, `More`, and `Unavailable until this feature is integrated.`

- [ ] **Step 3: Render phone dark teal preview**

Run:

```bash
android studio render-compose-preview \
  --project POS-ERPNext \
  --pid <PID> \
  --output-image-file=app/build/reports/mobile-pos-task2b/previews/phone-dark-teal.png \
  --print-semantics \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt \
  FoundationDarkTealPreview \
  > app/build/reports/mobile-pos-task2b/previews/phone-dark-teal.semantics.txt
```

Expected: exit 0 with same five root labels and unavailable state.

- [ ] **Step 4: Render phone landscape 1.5x preview**

Run:

```bash
android studio render-compose-preview \
  --project POS-ERPNext \
  --pid <PID> \
  --output-image-file=app/build/reports/mobile-pos-task2b/previews/phone-landscape-font-1.5.png \
  --print-semantics \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt \
  FoundationPhoneLandscapeFontScalePreview \
  > app/build/reports/mobile-pos-task2b/previews/phone-landscape-font-1.5.semantics.txt
```

Expected: exit 0; primary content and all five root labels remain visible with no clipping or overlap.

- [ ] **Step 5: Render tablet portrait preview**

Run:

```bash
android studio render-compose-preview \
  --project POS-ERPNext \
  --pid <PID> \
  --output-image-file=app/build/reports/mobile-pos-task2b/previews/tablet-portrait.png \
  --print-semantics \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt \
  FoundationTabletPortraitPreview \
  > app/build/reports/mobile-pos-task2b/previews/tablet-portrait.semantics.txt
```

Expected: exit 0; bounded content and all five navigation actions remain visible.

- [ ] **Step 6: Render tablet landscape preview**

Run:

```bash
android studio render-compose-preview \
  --project POS-ERPNext \
  --pid <PID> \
  --output-image-file=app/build/reports/mobile-pos-task2b/previews/tablet-landscape.png \
  --print-semantics \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/FoundationPreviews.kt \
  FoundationTabletLandscapePreview \
  > app/build/reports/mobile-pos-task2b/previews/tablet-landscape.semantics.txt
```

Expected: exit 0; dark teal expanded layout has no clipping, overlap, or content under navigation.

- [ ] **Step 7: Inspect all generated previews**

Read each PNG and semantics file. Require:

- Correct light/dark surfaces.
- Blue and teal accents visibly differ.
- Home unavailable state remains honest.
- Cashier remains centered and elevated.
- Five root actions appear once in visual order.
- Phone/tablet portrait/landscape content remains visible.
- Font scale 1.5 does not clip primary labels.
- No input, total, stock, payment, printer, sync, live report, or fake ERPNext data appears.

Stop and preserve artifacts if any requirement fails.

### Task 2: Run Fresh Automated Gates

**Files:**
- Test: `app/src/test/java/com/rotiropi/pos_erpnext/ui/PosFoundationTest.kt`
- Test: `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReleaseFixtureExclusionTest.kt`
- Test: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt`
- Generate: `app/build/reports/mobile-pos-devices/api23/*`
- Generate: `app/build/reports/mobile-pos-devices/api36/*`

**Interfaces:**
- Consumes: existing Task 2B implementation and tests.
- Produces: fresh unit, lint, build, and device evidence.

- [ ] **Step 1: Run focused UI unit tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.*"
```

Expected: PASS.

- [ ] **Step 2: Confirm release unit-test task absence**

Run:

```bash
./gradlew tasks --all | grep -E 'testReleaseUnitTest|compileReleaseKotlin|assembleDebugAndroidTest'
```

Expected: `compileReleaseKotlin` and `assembleDebugAndroidTest` exist; `testReleaseUnitTest` does not. Record absence, do not fabricate a pass.

- [ ] **Step 3: Run clean full available Gradle gate**

Run:

```bash
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest compileReleaseKotlin
```

Expected: exit 0.

- [ ] **Step 4: Run exact API 23 suite**

Run:

```bash
./tools/run-device-tests.sh api23
```

Expected: exit 0, deterministic API 23 identity, exactly `OK (5 tests)`, and artifacts under `app/build/reports/mobile-pos-devices/api23/`.

- [ ] **Step 5: Run exact API 36 suite**

Run:

```bash
./tools/run-device-tests.sh api36
```

Expected: exit 0, deterministic API 36 identity, exactly `OK (5 tests)`, and artifacts under `app/build/reports/mobile-pos-devices/api36/`.

- [ ] **Step 6: Inspect device evidence**

Require on both APIs:

- All five Compose tests pass.
- Home launches visibly.
- Root navigation selection and recreation pass.
- Root destinations and Cashier touch target pass.
- External keyboard traversal passes in visual order.
- No scanner text input exists before Cashier integration.
- Deterministic runtime-state check passes.

### Task 3: Review Diff and Update Status Only on Complete Evidence

**Files:**
- Modify only if Tasks 1 and 2 pass: `docs/mobile-pos/implementation-plan.md`
- Modify only if required for factual alignment: `docs/mobile-pos/testing-strategy.md`
- Read: Task 2B implementation files listed in `docs/mobile-pos/implementation-plan.md`

**Interfaces:**
- Consumes: complete preview, unit, lint, build, API 23, and API 36 evidence.
- Produces: accurate Task 2B status and final completion report.

- [ ] **Step 1: Run repository consistency checks**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; status preserves unrelated local work.

- [ ] **Step 2: Inspect Task 2B-only diff**

Run:

```bash
git diff -- \
  build.gradle.kts \
  gradle/libs.versions.toml \
  app/build.gradle.kts \
  app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt \
  app/src/main/java/com/rotiropi/pos_erpnext/ui \
  app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview \
  app/src/test/java/com/rotiropi/pos_erpnext/ui \
  app/src/androidTest/java/com/rotiropi/pos_erpnext/ui \
  app/src/main/res/drawable \
  app/src/main/res/layout \
  app/src/main/res/navigation \
  tools/run-device-tests.sh
```

Also inspect untracked files under those paths with `git status --short --untracked-files=all -- <paths>`.

Require no Task 2C surface, backend change, release fake data, unapproved endpoint, local accounting, camera, printer, sync, or extra dependency.

- [ ] **Step 3: Decide completion truthfully**

If every required Task 1 and Task 2 check passes, update Task 2B status to `Completed` with exact current evidence and set Task 2C as next incomplete task. Correct stale references from three to five Compose tests. If any requirement fails or remains unavailable, keep Task 2B `In Progress`, document the blocker, and do not start Task 2C.

- [ ] **Step 4: Re-run documentation diff checks after any status edit**

Run:

```bash
git diff --check
git diff -- docs/mobile-pos/implementation-plan.md docs/mobile-pos/testing-strategy.md
git status --short
```

Expected: factual evidence only; no temporary session notes.

- [ ] **Step 5: Report completion and stop**

Report in Bahasa Indonesia:

- Changes made.
- Files changed.
- Tests added or updated.
- Exact commands run.
- Actual unit, lint, build, API 23, API 36, preview, semantics, keyboard, and scanner-focus results.
- Checks not run and reasons.
- Remaining risks.
- Targeted diff summary and current short status.
- Proposed commit message `feat: add Compose POS foundation`.
- Explicit confirmation that no commit or push occurred.

Do not start Task 2C.
