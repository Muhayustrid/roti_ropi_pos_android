# Prototype Grand Total Font Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the Grand Total amount size in the narrow cart layout so it does not visually overwhelm the cart summary or payment button.

**Architecture:** Keep the existing responsive branch in `CartSheetContent`. Override only the narrow-screen amount typography with an explicit smaller size; leave data, calculations, checkout behavior, and wide-screen layout unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Android build, API 25 emulator.

## Global Constraints

- Change only the narrow-screen Grand Total amount in `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`.
- Keep the existing two-row layout on narrow screens.
- Keep the wide-screen layout, total calculation, labels, and checkout behavior unchanged.
- Do not add dependencies or change production `app/` code.
- Do not commit changes unless separately authorized.

---

### Task 1: Reduce Narrow Grand Total Typography

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt:524-539`
- Test: Manual API 25 cart verification; no relevant automated prototype test currently covers this composable branch.

**Interfaces:**
- Consumes: Existing `grandTotal`, `formatRupiah`, `PriceDisplay`, and `BoxWithConstraints` branch.
- Produces: Same cart summary and checkout behavior with smaller narrow-screen Grand Total amount.

- [x] **Step 1: Change only narrow-screen amount typography**

Keep narrow layout and alignment unchanged. Replace the narrow branch amount style with an explicit smaller copy:

```kotlin
Text(
    "Rp ${formatRupiah(grandTotal)}",
    modifier = Modifier.fillMaxWidth(),
    style = PriceDisplay.copy(fontSize = 32.sp, lineHeight = 38.sp),
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.End,
)
```

Do not change the wide branch, `grandTotal` calculation, CTA, or surrounding spacing.

- [x] **Step 2: Compile Kotlin**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Build and lint**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Expected: both commands finish with `BUILD SUCCESSFUL` and no new lint errors.

- [x] **Step 4: Install and launch latest APK on API 25**

Run:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -n com.rotiropi.pos_prototype/.MainActivity
```

Expected: install reports `Success`; `MainActivity` opens.

- [x] **Step 5: Verify cart appearance**

Open the cart on the `mobile-pos-api25` emulator and verify:

- `Grand Total` remains on its own label row.
- The nominal remains right-aligned below the label.
- The nominal is visibly smaller than the previous `PriceDisplay` size.
- Summary divider, payment button, and bottom sheet bounds do not overlap.
- Wide-screen behavior remains unchanged by the narrow-only style override.
