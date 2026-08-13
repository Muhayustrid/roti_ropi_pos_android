# Prototype Adaptive UI Test Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make prototype navigation and cart presentation respond to measured Compose constraints, then cover breakpoint and cart/customer/checkout behavior with device Compose tests.

**Architecture:** `PosShell` classifies its own `BoxWithConstraints.maxWidth`, preventing a constrained host from using device-wide configuration. `CashierScreen` measures its post-rail content width; a persistent cart needs at least 720dp, enough for a 360dp panel, gaps/padding, and readable two-column catalog. Test-only tags identify navigation containers and modal/persistent cart roots, while Compose behavior tests drive real screen state from a `Box` with forced Compose constraints.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Compose UI Test JUnit4, AndroidX Test, API 23+.

## Global Constraints

- Modify only `prototype/android-prototype` and this plan; do not modify production `app/` or backend code.
- Keep primary action color `#5F7DF7`, semantic Material roles, minSdk 23, and existing dependencies.
- Preserve `Navigation.kt` routes, `SessionState`, catalog filtering, cart arithmetic, customer selection, checkout callbacks, payment flow, and opening/closing behavior.
- Compact is `<600dp`; medium is `600dp..839dp`; expanded shell is `>=840dp`.
- Bottom navigation is compact-only; navigation rail is medium/expanded-only.
- A persistent cart is selected from measured Cashier content width, not device width, and requires `>=720dp` to preserve a 360dp cart and readable catalog.
- Do not commit, push, or change unrelated existing work.

---

### Task 1: Make Layout Decisions Constraint-Aware

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosShell.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosBottomBar.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/layout/PosLayoutMode.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`
- Modify: `prototype/android-prototype/app/src/test/java/com/rotiropi/pos_prototype/ui/layout/PosLayoutModeTest.kt`

**Interfaces:**
- Consumes: `posLayoutModeForWidthDp(widthDp: Int): PosLayoutMode` and `usesPersistentCart(widthDp: Int): Boolean`.
- Produces: shell chrome selected from parent constraints; cart mode selected from Cashier content constraints; test tags `pos_bottom_navigation`, `pos_navigation_rail`, `cashier_cart_sheet`, and `cashier_persistent_cart`.

- [ ] **Step 1: Write failing helper tests for the content-width cart threshold**

Replace the persistent-cart test with:

```kotlin
@Test
fun persistentCartRequiresDedicatedContentWidth() {
    assertEquals(false, usesPersistentCart(719))
    assertEquals(true, usesPersistentCart(720))
    assertEquals(true, usesPersistentCart(1000))
}
```

- [ ] **Step 2: Run the focused unit test and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*PosLayoutModeTest.persistentCartRequiresDedicatedContentWidth'
```

Expected: failure because `usesPersistentCart(720)` currently returns `false` by using the 840dp shell breakpoint.

- [ ] **Step 3: Implement the minimum width contract**

In `PosLayoutMode.kt`, add:

```kotlin
private const val MIN_PERSISTENT_CART_CONTENT_WIDTH_DP = 720

fun usesPersistentCart(contentWidthDp: Int): Boolean =
    contentWidthDp >= MIN_PERSISTENT_CART_CONTENT_WIDTH_DP
```

Keep `posLayoutModeForWidthDp` unchanged. Update comments to distinguish shell breakpoint from Cashier content threshold.

- [ ] **Step 4: Run the focused unit test and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Move shell chrome classification to its measured width**

Wrap `PosShell` body in `BoxWithConstraints(modifier = modifier.fillMaxSize())`, calculate:

```kotlin
val layoutMode = posLayoutModeForWidthDp(maxWidth.value.toInt())
```

Remove `LocalConfiguration`. Keep same `Scaffold` padding contract and exactly one navigation component. Add `Modifier.testTag("pos_bottom_navigation")` to `PosBottomBar`'s outer `Surface`, and `Modifier.testTag("pos_navigation_rail")` to `PosNavigationRail`'s `NavigationRail`.

- [ ] **Step 6: Derive cart mode from measured Cashier content width**

Move Cashier content and both bottom sheets inside a `BoxWithConstraints` immediately after applying scaffold `innerPadding`:

```kotlin
val persistentCart = usesPersistentCart(maxWidth.value.toInt())
```

Remove `LocalConfiguration`. Keep the existing `showCart` and `showSelectCustomer` state, checkout lambda, customer callbacks, and catalog/cart branches. Add `Modifier.testTag("cashier_persistent_cart")` to expanded cart `Surface`, and set the root `CartSheetContent` modifier in the modal call to `Modifier.testTag("cashier_cart_sheet")`.

- [ ] **Step 7: Run all local unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Add Compose Behavior Coverage

**Files:**
- Create: `prototype/android-prototype/app/src/androidTest/java/com/rotiropi/pos_prototype/ui/AdaptiveCashierScreenTest.kt`

**Interfaces:**
- Consumes: `POSPrototypeTheme`, `PosShell`, `CashierScreen`, `NavTab`, and task-one test tags.
- Produces: emulator-backed coverage of width boundaries, exclusive chrome, modal/persistent cart presentation, customer return behavior, and unchanged checkout totals.

- [ ] **Step 1: Create the failing instrumented Compose tests**

Use `createAndroidComposeRule<ComponentActivity>()`. Define:

```kotlin
private fun setCashierContent(width: Int, onCheckout: (Double, Double, Double) -> Unit = { _, _, _ -> }) {
    rule.setContent {
        DeviceConfigurationOverride(
            DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 900.dp)),
        ) {
            POSPrototypeTheme { CashierScreen(onCheckout = onCheckout) }
        }
    }
}
```

Wrap each screen in `Box(Modifier.requiredSize(width.dp, 900.dp))` so the test exercises production constraint measurement independently of the physical emulator. Add these tests:

```kotlin
@Test fun compact599ShowsOnlyBottomNavigation()
@Test fun medium600And839ShowOnlyNavigationRail()
@Test fun expanded840ShowsRailAndPersistentCart()
@Test fun compactCartReturnsAfterSelectingCustomer()
@Test fun compactCheckoutPreservesSubtotalTaxAndGrandTotal()
```

For chrome tests, assert exact presence/absence using task-one tags. For compact cart flow: add `Roti Manis`, open `View Cart`, tap `Change customer`, choose a registered customer, tap `Use Customer`, then assert `cashier_cart_sheet` and selected customer text. For checkout: add `Roti Manis` and `Croissant Butter`, open cart, tap `Continue to Payment`, and assert captured values equal `30_500.0`, `3_050.0`, `33_550.0`.

- [ ] **Step 2: Run instrumented test class and verify RED**

Run with connected emulator:

```bash
./gradlew :app:connectedDebugAndroidTest --tests 'com.rotiropi.pos_prototype.ui.AdaptiveCashierScreenTest'
```

Expected: compilation failure until task-one tags exist, then behavior failures while layout decisions still use global configuration.

- [ ] **Step 3: Complete minimal production changes from Task 1**

Apply only Task 1 production code. Do not add test-only state, fake data, or navigation paths.

- [ ] **Step 4: Run instrumented class and verify GREEN**

Run the command from Step 2. Expected: all five tests pass on `emulator-5554`.

- [ ] **Step 5: Run complete required verification**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 3: Final Scope and Device Review

**Files:**
- Inspect: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/**`
- Inspect: `prototype/android-prototype/app/src/androidTest/java/com/rotiropi/pos_prototype/**`

- [ ] **Step 1: Install the fresh debug APK and inspect compact and expanded screenshots**

Run:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify compact at physical density and expanded using `wm size 640x900` plus `wm density 120`; restore both overrides after inspection. Confirm one navigation component only, modal cart below 720dp Cashier content, and persistent cart at expanded width.

- [ ] **Step 2: Inspect scope and forbidden theme references**

Run:

```bash
rg -n 'Amber|amber|WarmCommerce|0xFF7B5800|0xFFFEBA11|0xFFFFBB12' app/src/main
git status --short --untracked-files=all -- prototype/android-prototype app/
```

Expected: no forbidden prototype theme references. No edits to production `app/`; its pre-existing dirty changes remain untouched.

- [ ] **Step 3: Do not commit or push**

Leave all intended files unstaged for explicit user review.
