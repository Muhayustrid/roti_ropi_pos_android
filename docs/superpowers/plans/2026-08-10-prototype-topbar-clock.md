# Prototype Top Bar Clock and Logout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the prototype POS top-bar logout action and show the application clock with seconds while preserving logout from More.

**Architecture:** Keep the existing shared `PosTopBar` and `PosShell` structure. Remove the top-bar logout API and caller wiring, but leave More's independent `LogoutGroup` callback intact. Keep `SimpleDateFormat`, changing only format and refresh cadence.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Android prototype module.

## Global Constraints

- Update Android prototype shared POS top bar only; production `app/` remains untouched.
- Remove logout icon and `onLogout` wiring from prototype `PosTopBar` and `PosShell`.
- Keep More screen logout action and callback unchanged.
- Use application clock format `h:mm:ss a` and refresh clock state every second.
- Leave Android system status-bar clock unchanged.
- Add no dependency, asset, or authentication behavior change.
- Do not commit or push.

---

### Task 1: Update Shared Top Bar

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosTopBar.kt:10-103`

**Interfaces:**
- Consumes: existing `PosTopBar(modifier: Modifier = Modifier)` call from `PosShell`.
- Produces: shared top bar with storefront, title, schedule icon, and live `h:mm:ss a` clock.

- [ ] **Step 1: Remove top-bar logout API and icon**

  Remove `Icons.Filled.Logout`, `IconButton`, and `onLogout` from `PosTopBar`. Update the KDoc so it no longer claims the header signs out.

- [ ] **Step 2: Update clock cadence and format**

  Change the existing values to:

  ```kotlin
  val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

  LaunchedEffect(Unit) {
      while (true) {
          delay(1_000L)
          now = System.currentTimeMillis()
      }
  }
  ```

- [ ] **Step 3: Compile the affected module**

  Run `./gradlew :app:compileDebugKotlin` from `prototype/android-prototype`.

  Expected: `BUILD SUCCESSFUL`.

### Task 2: Remove Dead Prototype Wiring

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosShell.kt:18-29`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt:76-109`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/history/HistoryScreen.kt:88-109`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt:44-56`
- Modify: `prototype/android-prototype/Navigation.kt:102-120,144-170`

**Interfaces:**
- Consumes: `MoreScreen(onLogout = logout)` from `Navigation.kt`.
- Produces: Cashier, History, and More screens using `PosShell` without a top-bar logout callback.

- [ ] **Step 1: Remove `onLogout` from `PosShell`**

  Change the signature from:

  ```kotlin
  fun PosShell(
      activeTab: NavTab,
      onTabSelected: (NavTab) -> Unit,
      onLogout: () -> Unit = {},
      modifier: Modifier = Modifier,
      content: @Composable (PaddingValues) -> Unit,
  )
  ```

  to the same signature without `onLogout`, and change `topBar = { PosTopBar(onLogout = onLogout) }` to `topBar = { PosTopBar() }`.

- [ ] **Step 2: Remove unused Cashier and History parameters**

  Remove `onLogout: () -> Unit` from `CashierScreen` and `HistoryScreen`, and remove their `onLogout = onLogout` arguments when calling `PosShell`.

- [ ] **Step 3: Preserve More logout**

  Keep `MoreScreen`'s `onLogout` parameter and its `LogoutGroup(..., onLogout, ...)` calls. Remove only `onLogout = onLogout` from its `PosShell` call.

- [ ] **Step 4: Update Navigation callers**

  Remove `onLogout = logout` only from `entry<Cashier>` and `entry<History>`. Keep `onLogout = logout` for `OpeningBalance` and `More`.

- [ ] **Step 5: Check prototype references**

  Run `rg -n "PosTopBar|PosShell|onLogout" prototype/android-prototype/app/src/main/java prototype/android-prototype/Navigation.kt` and confirm no Cashier/History top-bar wiring remains while More and Opening logout paths remain.

### Task 3: Verify Prototype UI

**Files:**
- Inspect: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosTopBar.kt`
- Inspect: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`

**Interfaces:**
- Consumes: updated shared top bar and More logout path.
- Produces: verified debug APK and emulator evidence.

- [ ] **Step 1: Run formatting and build checks**

  Run:

  ```bash
  git diff --check
  ./gradlew :app:lintDebug :app:assembleDebug
  ```

  Expected: both commands exit successfully.

- [ ] **Step 2: Install and inspect emulator**

  Install `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`, open Cashier, and confirm top bar shows time such as `9:28:05 PM` with no logout icon. Confirm More still exposes logout action.

- [ ] **Step 3: Record known test limitation**

  `./gradlew :app:testDebugUnitTest` may remain blocked by the pre-existing `com.example.posprototype` scaffold tests. Do not modify that unrelated scaffold in this task.
