# Opening Field UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve native Opening Session amount editing and presentation with an isolated selection-aware Compose field while preserving all Task 6 request and recovery behavior.

**Architecture:** `OpeningAmountField` owns internal `TextFieldValue`, raw normalization, selection, cursor mapping, and display transformation. `OpeningScreen` remains the only screen integration point and passes ungrouped raw values to the existing `OpeningViewModel`; existing canonicalization remains responsible for submit values and domain validation. No transport, persistence, recovery, DTO, UUID, serialization, or reconciliation code changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `TextFieldValue`, `VisualTransformation`, `OffsetMapping`, `KeyboardType.Decimal`, JUnit, Compose UI tests, existing `PosTheme`.

## Global Constraints

- Communicate with user in Indonesian; repository code, tests, comments, docs, and commit messages remain English.
- Use main agent only; no subagents, background agents, parallel agents, reviewer agents, or autonomous task agents.
- Do not commit, push, merge, publish, deploy, or modify protected IDE/CodeGraph files.
- Do not change backend code, Task 7, DTOs, request serialization, UUID generation, durable recovery, reconciliation, or capability refresh.
- Preserve server payment-mode order, server minimum, editable policy, canonical decimal-dot output, scale, and no-rounding behavior.
- Use no Float, Double, implicit floating-point conversion, local rounding, or local truncation.
- Keep raw editable, display, and canonical submit representations separate.
- Use internal `TextFieldValue`; external callbacks emit only ungrouped raw input.
- Treat valid Indonesian formatted paste as presentation input; reject malformed grouping safely.
- Keep existing Material 3 theme; no new dependency and no global theme rewrite.
- Use TDD: focused failing tests before production implementation.

---

### Task 1: Establish failing amount-editing tests

**Files:**
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountFieldTest.kt`
- Modify: `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountCanonicalizerTest.kt`

**Interfaces:**
- Produces test expectations for pure helpers that will be defined in `OpeningAmountField.kt`: raw paste normalization, display transformation, and numerical offset mapping.
- Consumes existing `OpeningAmountInputPolicy` and `canonicalizeOpeningAmount` without changing their contract.

- [ ] **Step 1: Add failing raw normalization tests**

Add tests that require these exact results:

```kotlin
assertEquals("1000", normalizeOpeningAmountInput("1.000"))
assertEquals("10000,50", normalizeOpeningAmountInput("10.000,50"))
assertEquals("1000000,25", normalizeOpeningAmountInput("1.000.000,25"))
assertNull(normalizeOpeningAmountInput("1,000.50"))
assertNull(normalizeOpeningAmountInput("1.00.0,50"))
assertNull(normalizeOpeningAmountInput("10..000"))
assertNull(normalizeOpeningAmountInput("10,00,50"))
```

Also cover signs, exponent syntax, whitespace, symbols, invalid group lengths, and empty input as an allowed intermediate state where the field editor needs it.

- [ ] **Step 2: Add failing display and decimal-preservation tests**

Require raw/display separation:

```kotlin
assertEquals("10.000", formatOpeningAmount("10000"))
assertEquals("10.000,5", formatOpeningAmount("10000,5"))
assertEquals("10.000,50", formatOpeningAmount("10000,50"))
assertEquals("10.000,50", formatOpeningAmount("10000.50"))
assertEquals("10.000,", formatOpeningAmount("10000,"))
```

Require canonical output to remain existing behavior:

```kotlin
assertEquals(OpeningAmountResult.Valid("10000.50"), canonicalizeOpeningAmount("10000,5", policy))
assertEquals(OpeningAmountResult.Valid("10000.50"), canonicalizeOpeningAmount("10000,50", policy))
```

- [ ] **Step 3: Add failing offset mapping tests**

Define expected numerical cursor behavior around inserted grouping dots for both directions. Test positions before, at, and after visual separators. For raw `10000`, transformed `10.000`, mapping must preserve numerical digit position and place a cursor adjacent to the intended digit, not merely reuse visual character index.

- [ ] **Step 4: Add failing editing behavior tests**

Cover:

```kotlin
// zero replacement
rawAfterInput(raw = "0", selection = TextRange(1), inserted = "1") == "1"

// grouped-number backspace semantics
rawAfterBackspace(raw = "10000", cursor = end) == "1000"
rawAfterBackspace(raw = "10000,50", cursor = end) == "10000,5"
```

Cover selection-all replacement, clear-to-empty, paste normalization, trailing decimal preservation, large exact strings, and invalid mixed separators.

- [ ] **Step 5: Add failing request/body safety assertions**

In existing `OpeningViewModelTest.kt`, add a test that submits a logically grouped UI value through the field callback and asserts the resulting `OpenSessionRequestDto` amount is ASCII decimal-dot with no grouping. Add an exact large-value case to prove no precision loss. Do not add source-text searches for `Float` or `Double`.

- [ ] **Step 6: Run focused tests and record expected failures**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.opening.OpeningAmountFieldTest" --tests "com.rotiropi.pos_erpnext.ui.opening.OpeningAmountCanonicalizerTest" --tests "com.rotiropi.pos_erpnext.ui.opening.OpeningViewModelTest"
```

Expected: new helper symbols or behavior are missing; existing tests must remain green except for intentionally added tests that expose the missing implementation.

---

### Task 2: Implement raw input normalization and display transformation

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountField.kt`
- Test: `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountFieldTest.kt`

**Interfaces:**
- Produces internal pure helpers:
  - `internal fun normalizeOpeningAmountInput(input: String): String?`
  - `internal fun formatOpeningAmount(raw: String): String`
  - `internal fun openingAmountOffsetMapping(raw: String): OffsetMapping`
- Produces `@Composable fun OpeningAmountField(...)` with raw external callback and internal selection ownership.

- [ ] **Step 1: Implement strict presentation normalization**

Accept either ungrouped digits with one decimal separator (`.` or `,`) or structurally valid Indonesian grouping with comma decimal. Normalize valid grouping to ungrouped raw input. Reject mixed grouping syntax, bad group lengths, repeated separators, signs, exponent notation, whitespace, symbols, and malformed decimals. Allow empty raw input and trailing decimal only in editor state; let existing canonicalization decide submit validity.

- [ ] **Step 2: Implement string-only display formatting**

Format integer digits from right to left with `.` every three digits. Preserve raw fractional digits exactly. Render either raw decimal separator as `,`. Do not scale, round, truncate, or create canonical values.

- [ ] **Step 3: Implement bidirectional numerical offset mapping**

Map raw offsets to transformed offsets by counting digits and decimal position. Map transformed offsets back by ignoring grouping separators and preserving the decimal boundary. For a cursor adjacent to a grouping dot, choose the corresponding numeric boundary deterministically so backspace targets the intended digit.

- [ ] **Step 4: Run amount unit tests**

Run the focused `OpeningAmountFieldTest` and canonicalizer tests. Expected: normalization, formatting, decimal preservation, and mapping tests pass; editing tests may still fail until the composable editor is wired.

---

### Task 3: Implement selection-aware `OpeningAmountField`

**Files:**
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountField.kt`
- Test: `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountFieldTest.kt`

**Interfaces:**
- `OpeningAmountField` accepts raw `String`, label, enabled state, error state, supporting text, and `onValueChange: (String) -> Unit`.
- Internal state uses `TextFieldValue` and is synchronized only when the external raw value changes for a new server/UI state, not on every keystroke canonicalization.

- [ ] **Step 1: Add internal `TextFieldValue` state**

Initialize the internal value from external raw input with selection at the end, display through `VisualTransformation`, and use `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ...)`.

- [ ] **Step 2: Add zero selection and replacement behavior**

When the editable raw value represents server-suggested zero, display `0`; on first focus select the complete zero value when practical. On numeric input replacing a selected zero, emit raw `1` rather than `0.001`. Keep clear and trailing-decimal states editable.

- [ ] **Step 3: Add paste and edit normalization**

Normalize valid grouped paste before invoking the callback. Preserve ungrouped decimal dot/comma input. Reject malformed input without corrupting the existing valid raw value. Keep typed fractional digits and trailing decimal separator intact.

- [ ] **Step 4: Preserve selection by numerical meaning**

After normalization and display transformation, update selection through raw-to-transformed mapping. Do not calculate cursor position by blindly adding separator count to the prior visual offset. Ensure backspace adjacent to grouping separators removes the intended numeric digit.

- [ ] **Step 5: Add semantics and Material 3 field presentation**

Use visible label, error supporting text, `testTag`, `stateDescription` for locked/server-controlled state, and suitable content semantics. Keep minimum field height at `PosDimensions.touchTarget` and use existing theme tokens only.

- [ ] **Step 6: Run focused unit tests**

Run the amount field, canonicalizer, and ViewModel tests. Expected: all focused tests pass with no changes to domain canonicalization.

---

### Task 4: Integrate Layout A into `OpeningScreen`

**Files:**
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningScreen.kt`
- Test: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/opening/OpeningScreenTest.kt`

**Interfaces:**
- Consumes existing `OpeningUiState`, `RecoveryScreenState`, and callbacks.
- Produces unchanged callback contract: `onAmountChanged(modeOfPayment, rawInput)` and `onSubmit()`.

- [ ] **Step 1: Add failing Compose coverage**

Add tests for zero display, grouped amount, decimal display, focus/selection behavior, non-editable mode, disabled submit during submission/recovery, inline validation, unavailable configuration, narrow phone, and large font scale. Assert stable test tags and visible semantics rather than implementation details.

- [ ] **Step 2: Replace raw `OutlinedTextField` usage**

Render each server-provided payment mode in existing server order through `OpeningAmountField`. Keep non-editable rows disabled and visibly server-controlled. Do not change state or callback ownership.

- [ ] **Step 3: Add Layout A summary surface**

Use existing Material 3 `Surface`/card primitives and theme tokens. Label the section “Opening amounts”; show currency and POS Profile as context only. Do not calculate or display a client-side accounting total.

- [ ] **Step 4: Preserve state hierarchy**

Keep recovery UI visible, unavailable state without fallback inputs, inline row errors, global live-region errors, submitting state, recovery-pending state, and disabled submit behavior. Keep content scrollable and ensure submit remains reachable at narrow phone and large font scale.

- [ ] **Step 5: Run Compose instrumentation tests**

Run:

```bash
./gradlew connectedDebugAndroidTest --tests "com.rotiropi.pos_erpnext.ui.opening.OpeningScreenTest"
```

If managed device scripts are required, use the repository-supported API 23/API 36 scripts and document API 25 availability separately.

---

### Task 5: Add debug previews and visual verification

**Files:**
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/OpeningPreviews.kt`

**Interfaces:**
- Produces local-fixture previews only; no backend calls and no production state mutation.

- [ ] **Step 1: Add required preview fixtures**

Create previews for zero opening amount, `10.000`, decimal, invalid value, server minimum error, non-editable mode, submitting, recovery pending, dark theme, large font scale, compact phone, and phone landscape.

- [ ] **Step 2: Build debug preview source set**

Run `./gradlew assembleDebug` and fix only preview/source-set errors caused by this feature.

- [ ] **Step 3: Run visual comparison**

Compare Android against the rendered Next.js reference at 360×800, 412×915, and supported landscape. Record matched hierarchy, intentional native differences, remaining gaps, and accessibility findings. Do not claim pixel-perfect parity.

---

### Task 6: Verify Task 6 invariants and repository gates

**Files:**
- Modify only feature files if verification exposes a scoped defect.
- Do not modify Task 6 transport/recovery/serialization files.

- [ ] **Step 1: Prove request serialization invariance**

Run focused ViewModel/request tests and compare canonical JSON for equivalent logical values before/after formatting. Assert no grouping separators in the request body, unchanged UUID behavior, and unchanged persisted body semantics.

- [ ] **Step 2: Run repository gates**

Run sequentially:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
git diff --check
```

- [ ] **Step 3: Run available device checks**

Verify Opening Session on API 23, API 25 if prepared, and API 36. Include narrow phone, landscape, dark theme, and large font scale observations.

- [ ] **Step 4: Review scoped diff and status**

Confirm only Opening UI, isolated amount editing, focused tests/previews, and approved design/plan docs changed. Confirm no backend, Task 7, protected IDE/CodeGraph, or unrelated screen files changed. Keep all changes uncommitted.

- [ ] **Step 5: Decide response-drop rerun**

Do not rerun external response-drop only when tests and diff prove presentation/editing-only changes, canonical request JSON unchanged, no grouping in persisted body, UUID unchanged, and recovery/replay/reconciliation untouched. Otherwise stop with `NOT READY — external response-drop rerun required`.
