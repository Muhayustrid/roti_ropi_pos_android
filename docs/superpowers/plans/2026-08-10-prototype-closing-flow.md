# Prototype Closing Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prototype More closing sheet with the latest Stitch 4.1-4.4 closing flow and verify the complete click path back to More.

**Architecture:** Add three explicit Navigation 3 routes and three focused closing screens. `MoreScreen` only hands off through `onCloseShift`; each closing screen owns only its local prototype fixture/input state and communicates through explicit callbacks. A small shared `ClosingTopBar` keeps back navigation and title treatment consistent without adding a state host.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation 3, existing prototype theme and Gradle setup.

## Global Constraints

- Change only `prototype/android-prototype/`; do not modify production `app/`.
- Use Stitch project `7730357639584129534` and screens `ce4aaa8984134cfd84007b00a7444526`, `18716b2f60c44ff78e05edbe57b771c4`, `3e9886f6984b4fc5bfdbd7f6c07dfeaf`, and `334991f94bad476fbeac5c278580b087`.
- Keep fixture data local to prototype screens; add no backend, API, repository, persistence, idempotency, printer, or accounting behavior.
- Use empty counted fields in 4.2; `Review Closing` remains enabled and does not validate or recalculate the fixed summary.
- Closing path is `More -> ClosingBalance -> ConfirmClosing -> ShiftClosed -> More`.
- Closing screens have no POS bottom navigation; all actions are at least 48dp and content is scrollable/keyboard-safe.
- Remove obsolete `ConfirmClosingSheet.kt`; screen 3.7 is replaced by 4.3.
- Do not commit or push without explicit user approval.

---

### Task 1: Add More Handoff and Closing Balance Route

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/closing/ClosingTopBar.kt`
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/closing/ClosingBalanceScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/NavigationKeys.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`
- Delete: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/ConfirmClosingSheet.kt`

**Interfaces:**
- `MoreScreen` consumes `onCloseShift: () -> Unit`.
- `ClosingBalanceScreen` exposes `onBack: () -> Unit` and `onReviewClosing: () -> Unit`.
- `ClosingTopBar` consumes `title: String`, `onBack: () -> Unit`, and an optional `Modifier`.
- Produces `ClosingBalance` and `ConfirmClosing` navigation keys for later tasks.

- [ ] **Step 1: Add route keys**

Append these serializable keys to `NavigationKeys.kt`:

```kotlin
@Serializable
data object ClosingBalance : NavKey

@Serializable
data object ConfirmClosing : NavKey

@Serializable
data object ShiftClosed : NavKey
```

- [ ] **Step 2: Add shared closing top bar**

Create `ClosingTopBar.kt` with a back action, centered title, and no logout or
bottom-navigation action:

```kotlin
package com.rotiropi.pos_prototype.ui.closing

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ClosingTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
    }
}
```

- [ ] **Step 3: Replace More sheet state with callback**

Change the `MoreScreen` signature and `AccountCard` call:

```diff
 fun MoreScreen(
     onTabSelected: (NavTab) -> Unit,
     onLogout: () -> Unit,
     modifier: Modifier = Modifier,
+    onCloseShift: () -> Unit = {},
 ) {
-    var showClosingSheet by remember { mutableStateOf(false) }

     PosShell(
-        AccountCard(onCloseShift = { showClosingSheet = true })
+        AccountCard(onCloseShift = onCloseShift)
     }
 }

Remove the `ConfirmClosingSheet` import, the local `showClosingSheet` state, and
the `if (showClosingSheet)` block. Keep the existing account/session fixture,
More tabs, sign-out, appearance, and General cards unchanged.

- [ ] **Step 4: Implement Closing Balance screen**

Create `ClosingBalanceScreen.kt` with this public entry point and local state:

```kotlin
@Composable
fun ClosingBalanceScreen(
    onBack: () -> Unit,
    onReviewClosing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cashCounted by rememberSaveable { mutableStateOf("") }
    var qrisCounted by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = { ClosingTopBar("Closing Balance", onBack) },
        bottomBar = {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onReviewClosing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Review Closing", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ClosingWalletSummary()
            ClosingSessionCard()
            ReconciliationCard(
                label = "Cash",
                opening = "Rp 200.000",
                expected = "Rp 865.000",
                counted = cashCounted,
                onCountedChange = { cashCounted = it },
                differenceLabel = "Short -Rp 5.000",
            )
            ReconciliationCard(
                label = "QRIS",
                opening = "Rp 0",
                expected = "Rp 350.000",
                counted = qrisCounted,
                onCountedChange = { qrisCounted = it },
                differenceLabel = "Balanced Rp 0",
            )
            ClosingSummaryCard()
        }
    }
}
```

Define these private composables in the same file:

```kotlin
@Composable private fun ClosingWalletSummary()
@Composable private fun ClosingSessionCard()
@Composable private fun ReconciliationCard(
    label: String,
    opening: String,
    expected: String,
    counted: String,
    onCountedChange: (String) -> Unit,
    differenceLabel: String,
)
@Composable private fun ClosingSummaryCard()
```

Implement them with Stitch copy:
`Main Counter 01`, `REF-8492-A`, `Ahmad Rizky`, `Sudirman`, totals
`Rp 1.215.000`, `Rp 1.210.000`, and `-Rp 5.000`. Counted fields use
`OutlinedTextField`, `KeyboardOptions(keyboardType = KeyboardType.Number)`, and
start empty. Keep the fixed summary independent of field values.

- [ ] **Step 5: Wire More to Closing Balance and compile**

Import `ClosingBalanceScreen` in `Navigation.kt`. Pass the callback from the
existing `entry<More>` and add the first closing route:

```kotlin
entry<More> {
    MoreScreen(
        onTabSelected = { tab ->
            when (tab) {
                NavTab.Cashier -> {
                    val cashierIndex = backStack.indexOfLast { it == Cashier }
                    if (cashierIndex >= 0) {
                        while (backStack.lastOrNull() != Cashier) backStack.removeLastOrNull()
                    } else {
                        backStack.add(Cashier)
                    }
                }
                NavTab.History -> if (backStack.lastOrNull() != History) backStack.add(History)
                NavTab.More -> Unit
            }
        },
        onLogout = logout,
        onCloseShift = { backStack.add(ClosingBalance) },
        modifier = Modifier.safeDrawingPadding(),
    )
}
entry<ClosingBalance> {
    ClosingBalanceScreen(
        onBack = { backStack.removeLastOrNull() },
        onReviewClosing = { backStack.add(ConfirmClosing) },
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

Preserve the existing More tab handler rather than duplicating it in the new
screen. Run from `prototype/android-prototype/`:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Add Confirm Closing Screen

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/closing/ConfirmClosingScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`

**Interfaces:**
- `ConfirmClosingScreen` consumes `onBack`, `onEditAmounts`, and `onConfirm` callbacks.
- Produces `ShiftClosed` navigation transition through `Navigation.kt`.

- [ ] **Step 1: Create the refined confirmation screen**

Create this entry point:

```kotlin
@Composable
fun ConfirmClosingScreen(
    onBack: () -> Unit,
    onEditAmounts: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ClosingTopBar("Closing Balance", onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConfirmClosingHeader()
            ClosingActionWarning()
            ClosingIdentityCard()
            ClosingMethodTable()
            ClosingTotalsCard()
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Confirm & Close Shift", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onEditAmounts,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Edit Amounts", fontWeight = FontWeight.Bold) }
        }
    }
}
```

Define these private composables in the same file with no external state:

```kotlin
@Composable private fun ConfirmClosingHeader()
@Composable private fun ClosingActionWarning()
@Composable private fun ClosingIdentityCard()
@Composable private fun ClosingMethodTable()
@Composable private fun ClosingTotalsCard()
```

Use Stitch 4.3 fixtures: Cash in Drawer `Rp 4.250.000`, QRIS Total
`Rp 1.120.000`, Vouchers `Rp 150.000`, Ahmad Rizky, Main Counter 01,
REF-8492-A, Cash and QRIS expected/counted equal, Total Expected/Counted
`Rp 5.370.000`, and Total Difference `Rp 0`. Include a text warning that the
current Opening closes and new sales cannot be added.

- [ ] **Step 2: Wire Confirm Closing route**

Add this entry after `ClosingBalance`:

```kotlin
entry<ConfirmClosing> {
    ConfirmClosingScreen(
        onBack = { backStack.removeLastOrNull() },
        onEditAmounts = { backStack.removeLastOrNull() },
        onConfirm = { backStack.add(ShiftClosed) },
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

- [ ] **Step 3: Compile the route**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Add Shift Closed Terminal Receipt

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/closing/ShiftClosedScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`

**Interfaces:**
- `ShiftClosedScreen` consumes `onBack` and `onDone`.
- Produces the terminal receipt surface and returns through the existing More key.

- [ ] **Step 1: Create the receipt screen**

Create a scrollable terminal-style screen with:

```kotlin
@Composable
fun ShiftClosedScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ClosingTopBar("Shift Closed", onBack) },
        bottomBar = {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShiftClosedSuccessHeader()
            ReceiptDetailsCard()
            PaymentBreakdownCard()
            BalancedStatusCard()
            AutomatedReportFooter()
        }
    }
}
```

Define these private composables in the same file with no external state:

```kotlin
@Composable private fun ShiftClosedSuccessHeader()
@Composable private fun ReceiptDetailsCard()
@Composable private fun PaymentBreakdownCard()
@Composable private fun BalancedStatusCard()
@Composable private fun AutomatedReportFooter()
```

Use the exact fixture values from the spec: `RR-20231027-04`, `Ahmad S.`,
`Main Counter - Roti Ropi`, `OB-99210`, `142 Invoices`, `Rp 4.250.000`, Cash
`1.250k`, QRIS `3.000k`, difference `Rp 0`, `Shift Balanced`, and the Stitch
timestamp `27 Oct 2023 • 22:15:42`. Keep the success state readable without
depending only on the green/primary color.

- [ ] **Step 2: Wire Shift Closed and Done**

Add this route:

```kotlin
entry<ShiftClosed> {
    ShiftClosedScreen(
        onBack = { popToMore() },
        onDone = { popToMore() },
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

Define this local helper once in `PosPrototypeApp` so both actions use the same
behavior:

```kotlin
fun popToMore() {
    while (backStack.lastOrNull() != More && backStack.isNotEmpty()) {
        backStack.removeLastOrNull()
    }
}
```

Use the existing back stack type and do not add a second navigation controller.

- [ ] **Step 3: Compile the receipt route**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Full Closing Flow Verification

**Files:**
- Create directory: `prototype/design-refs/closing-verification/`
- Create screenshots: `01-more-ready.png`, `02-closing-balance.png`, `03-confirm-closing.png`, `04-shift-closed.png`

**Interfaces:**
- Uses the four connected prototype routes and the existing emulator package.
- Produces build/lint output, UI dump evidence, and visual screenshots.

- [ ] **Step 1: Run static verification**

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Expected: both commands report `BUILD SUCCESSFUL`. Existing unit and
instrumentation scaffold failures referencing `com.example.posprototype` are
reported separately and not changed.

- [ ] **Step 2: Install and launch the debug APK**

Run:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.rotiropi.pos_prototype
adb shell am start -n com.rotiropi.pos_prototype/.MainActivity
```

- [ ] **Step 3: Navigate to More and exercise every closing action**

Use the existing prototype path to Cashier, tap More, and verify:

1. More displays the existing ready-to-close card and `Close Shift` opens 4.2.
2. 4.2 shows empty editable Cash/QRIS counted inputs and `Review Closing` opens 4.3.
3. 4.3 back and `Edit Amounts` return to 4.2.
4. 4.3 `Confirm & Close Shift` opens 4.4.
5. 4.4 `Done` and back both return to More.

- [ ] **Step 4: Capture screenshots and inspect UI semantics**

Capture the four named screenshots. For each screen run:

```text
adb shell uiautomator dump /sdcard/closing-ui.xml
adb pull /sdcard/closing-ui.xml /tmp/closing-ui.xml
```

Confirm expected text, action visibility, input labels, receipt totals, and no
system-bar overlap. Inspect the screenshots for clipping on the existing mobile
emulator.

- [ ] **Step 5: Inspect the intended worktree**

Run:

```text
git diff --check
git status --short -- prototype docs/superpowers/specs/2026-08-10-prototype-closing-flow-design.md docs/superpowers/plans/2026-08-10-prototype-closing-flow.md
```

Expected: closing-related work is limited to the prototype closing files, the
approved spec/plan, and screenshots. Do not revert unrelated worktree changes.
