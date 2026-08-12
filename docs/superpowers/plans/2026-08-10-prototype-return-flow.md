# Prototype Return Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a functional prototype return form and submitted state behind the existing successful transaction `Start Return` button.

**Architecture:** Add two explicit Navigation 3 keys and two focused Composable screens under `ui/returning`. Keep item quantities, reason, refund mode, and refund total local to `ReturnScreen`; `Navigation.kt` owns only route transitions. `ReturnSuccessScreen` is a fixed fixture receipt and returns to History on Done.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation 3, existing prototype theme and Gradle setup.

## Global Constraints

- Change only `prototype/android-prototype/`; do not modify production `app/`.
- Keep production return API, quote, validation, persistence, recovery, and accounting behavior unchanged.
- Use local fixture data for successful `#TRX-9402` only.
- `Start Return` is available only from existing successful transaction detail state.
- Default return quantity is `Roti Manis = 1`, other items `0`; reason is `Damaged Item`; refund mode is `Cash`.
- `Submit Return` is enabled for prototype demonstration and does not call backend.
- Keep actions at least 48dp, content scrollable, and quantity inputs keyboard-safe.
- Do not commit or push without explicit user approval.

---

### Task 1: Add Return Form and Transaction Detail Route

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/returning/ReturnScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/NavigationKeys.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`

**Interfaces:**
- `ReturnScreen` consumes `transactionId: String`, `onBack: () -> Unit`, and `onSubmit: () -> Unit`.
- `Return` and `ReturnSuccess` keys carry `transactionId: String`.
- `TransactionDetailScreen` continues to own the visible `Start Return` button; Navigation supplies its callback.

- [ ] **Step 1: Add serializable return keys**

Append to `NavigationKeys.kt`:

```kotlin
@Serializable
data class Return(val transactionId: String) : NavKey

@Serializable
data class ReturnSuccess(val transactionId: String) : NavKey
```

- [ ] **Step 2: Implement Return Sale screen state**

Create `ReturnScreen.kt` with this local model and entry point:

```kotlin
private data class ReturnLine(
    val name: String,
    val originalQuantity: Int,
    val unitPrice: Long,
    val quantity: String,
)

@Composable
fun ReturnScreen(
    transactionId: String,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lines by remember {
        mutableStateOf(
            listOf(
                ReturnLine("Roti Manis", 2, 12_000L, "1"),
                ReturnLine("Croissant", 3, 18_500L, "0"),
                ReturnLine("Pain au Chocolat", 2, 22_000L, "0"),
            ),
        )
    }
    var reason by rememberSaveable { mutableStateOf("Damaged Item") }
    var refundMode by rememberSaveable { mutableStateOf("Cash") }

    val refundTotal = lines.sumOf { line ->
        (line.quantity.toIntOrNull() ?: 0) * line.unitPrice
    }
    val selectedItemCount = lines.count { (it.quantity.toIntOrNull() ?: 0) > 0 }

    Scaffold(
        modifier = modifier,
        topBar = { ReturnTopBar(onBack) },
        bottomBar = { ReturnSubmitBar(refundTotal, onSubmit) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceTransactionCard(transactionId)
            Text("Select Items to Return", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            lines.forEachIndexed { index, line ->
                ReturnLineCard(
                    line = line,
                    onQuantityChange = { value ->
                        lines = lines.toMutableList().also {
                            it[index] = line.copy(quantity = value.filter(Char::isDigit))
                        }
                    },
                )
            }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Return Reason") },
                minLines = 2,
            )
            RefundModeSelector(refundMode) { refundMode = it }
            ReturnSummaryCard(selectedItemCount, refundTotal, refundMode)
        }
    }
}
```

Define these private composables in the same file:

```kotlin
@Composable private fun ReturnTopBar(onBack: () -> Unit)
@Composable private fun SourceTransactionCard(transactionId: String)
@Composable private fun ReturnLineCard(line: ReturnLine, onQuantityChange: (String) -> Unit)
@Composable private fun RefundModeSelector(selected: String, onSelected: (String) -> Unit)
@Composable private fun ReturnSummaryCard(itemCount: Int, refundTotal: Long, refundMode: String)
@Composable private fun ReturnSubmitBar(refundTotal: Long, onSubmit: () -> Unit)
```

Use source fixture `#TRX-9402`, `Oct 24, 2026 • 14:20`, `Walk-in Customer`,
`Rp 155.000`, and `Paid with QRIS`. `ReturnLineCard` shows original quantity,
unit price, numeric `OutlinedTextField` labeled `Return Quantity`, and line
refund amount. `RefundModeSelector` exposes Cash and QRIS `FilterChip`s.
`ReturnSubmitBar` shows `Refund Total` and a 56dp `Submit Return` button.

- [ ] **Step 3: Wire Start Return and Return form route**

Import `ReturnScreen` in `Navigation.kt`. Update the existing transaction detail
entry so its callback is no longer a no-op:

```kotlin
entry<TransactionDetail> { key ->
    TransactionDetailScreen(
        transactionId = key.transactionId,
        onClose = { backStack.removeLastOrNull() },
        onStartReturn = { backStack.add(Return(key.transactionId)) },
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

Add the form route:

```kotlin
entry<Return> { key ->
    ReturnScreen(
        transactionId = key.transactionId,
        onBack = { backStack.removeLastOrNull() },
        onSubmit = { backStack.add(ReturnSuccess(key.transactionId)) },
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

- [ ] **Step 4: Compile the form route**

Run from `prototype/android-prototype/`:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Add Return Submitted Screen and History Return

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/returning/ReturnSuccessScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`

**Interfaces:**
- `ReturnSuccessScreen` consumes `transactionId: String`, `onBack: () -> Unit`, and `onDone: () -> Unit`.
- `Navigation.kt` provides a History pop helper and owns the success route.

- [ ] **Step 1: Implement submitted state**

Create a scrollable receipt-style screen with this entry point:

```kotlin
@Composable
fun ReturnSuccessScreen(
    transactionId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ReturnSuccessTopBar(onBack) },
        bottomBar = { ReturnDoneBar(onDone) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReturnSuccessHeader()
            ReturnSuccessDetailsCard(transactionId)
            ReturnSuccessAmountCard()
        }
    }
}
```

Define these private composables in the same file:

```kotlin
@Composable private fun ReturnSuccessTopBar(onBack: () -> Unit)
@Composable private fun ReturnSuccessHeader()
@Composable private fun ReturnSuccessDetailsCard(transactionId: String)
@Composable private fun ReturnSuccessAmountCard()
@Composable private fun ReturnDoneBar(onDone: () -> Unit)
```

Use exact copy: `Return Submitted`, `Submitted`, `RET-TRX-9402-01`, original
transaction `#TRX-9402`, `Walk-in Customer`, refund `Rp 12.000`, refund mode
`Cash`, and `Roti Manis x1`. Include a success icon and visible status text.

- [ ] **Step 2: Wire success route and pop to History**

Add this helper near existing `logout`/navigation helpers:

```kotlin
val popToHistory: () -> Unit = {
    if (backStack.none { it == History }) {
        backStack.add(History)
    } else {
        while (backStack.lastOrNull() != History) {
            backStack.removeLastOrNull()
        }
    }
}
```

Add the route:

```kotlin
entry<ReturnSuccess> { key ->
    ReturnSuccessScreen(
        transactionId = key.transactionId,
        onBack = { backStack.removeLastOrNull() },
        onDone = popToHistory,
        modifier = Modifier.safeDrawingPadding(),
    )
}
```

- [ ] **Step 3: Compile the success route**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Build and Emulator Verification

**Files:**
- Create directory: `prototype/design-refs/return-verification/`
- Create screenshots: `01-return-sale.png`, `02-return-submitted.png`

**Interfaces:**
- Uses the existing History -> Transaction Detail route and new return routes.
- Produces build/lint output, UI dump evidence, and screenshots.

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

- [ ] **Step 3: Verify the return journey**

Use the existing prototype path to History and verify:

1. Open successful `#TRX-9402` and tap `Start Return`.
2. Confirm source details, three return items, default quantity/reason/Cash,
   and initial refund total `Rp 12.000`.
3. Change a quantity and confirm refund total updates.
4. Tap `Submit Return` and confirm Return Submitted.
5. Tap `Done` and confirm History is visible.
6. Press back from Return Sale and confirm Transaction Detail returns.

- [ ] **Step 4: Capture evidence and inspect semantics**

Capture the two named screenshots. For each screen run:

```text
adb shell uiautomator dump /sdcard/return-ui.xml
adb pull /sdcard/return-ui.xml /tmp/return-ui.xml
```

Confirm expected text, action visibility, numeric inputs, refund total, and no
system-bar overlap.

- [ ] **Step 5: Inspect intended worktree**

Run:

```text
git diff --check
git status --short -- prototype docs/superpowers/specs/2026-08-10-prototype-return-flow-design.md docs/superpowers/plans/2026-08-10-prototype-return-flow.md
```

Expected: return-related work is limited to prototype return files, the
approved spec/plan, and screenshots. Do not revert unrelated worktree changes.
