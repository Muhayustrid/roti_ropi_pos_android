# Prototype Payment Change Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept cash overpayment in the prototype and display the calculated change on Payment Entry and its confirmation dialog.

**Architecture:** Keep payment arithmetic in `PaymentCashScreen`, derive a non-negative `remaining` and `change`, and pass the existing callback's change field through unchanged. Add an optional `change` value to the shared confirmation dialog so QRIS, card, and split callers retain current behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Android build, API 25 emulator.

## Global Constraints

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt`.
- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt`.
- Keep exact-payment behavior, underpayment validation, keypad input, quick amounts, and navigation unchanged.
- Do not change production `app/` code or add dependencies.
- Do not commit or push unless separately authorized.

---

### Task 1: Accept Overpayment and Show Change in Payment Entry

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt:45-318`
- Test: Manual underpayment, exact-payment, and overpayment API 25 flow.

**Interfaces:**
- Consumes: Existing `grandTotal`, `input`, `onConfirm(cashReceived: Long, change: Long)`, and responsive summary tokens.
- Produces: `change` value passed to the confirmation dialog and `onConfirm` callback.

- [x] **Step 1: Replace overpayment state derivations**

Keep `cashReceived` parsing unchanged. Replace the current exact/overpaid derivations with:

```kotlin
val remaining = (grandTotal - cashReceived).coerceAtLeast(0L)
val change = (cashReceived - grandTotal).coerceAtLeast(0L)
val isSettled = cashReceived >= grandTotal
```

Update the KDoc sentence to say cash payment accepts exact or overpaid amounts; remove the `isOverpaid` error-only state.

- [x] **Step 2: Add conditional Change summary row**

After the existing `Remaining` row, render:

```kotlin
if (change > 0L) {
    SummaryRow(
        label = "Change",
        value = "Rp ${formatRupiah(change)}",
        compact = isCompact,
        emphasized = true,
    )
}
```

Extend `SummaryRow` with `emphasized: Boolean = false`. Use primary color for the value when emphasized and existing on-surface color otherwise. Keep compact typography behavior unchanged.

- [x] **Step 3: Enable settlement and forward change**

Change Complete Payment and confirmation callback only:

```kotlin
Button(
    onClick = { showConfirmDialog = true },
    enabled = isSettled,
    // Keep existing modifier, shape, and colors.
)
```

Pass change into dialog and callback:

```kotlin
PaymentConfirmationDialog(
    total = grandTotal,
    customerName = com.rotiropi.pos_prototype.data.SessionState.customerName,
    methodDescription = "Cash (Rp ${formatRupiah(cashReceived)})",
    itemCount = itemCount,
    change = change,
    onConfirm = {
        showConfirmDialog = false
        onConfirm(cashReceived, change)
    },
    onBack = { showConfirmDialog = false },
)
```

Remove only the old `Overpayment is not supported` error text. Keep keypad input, quick amounts, remaining expression, and all navigation intact.

### Task 2: Show Change in Confirmation Dialog

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt:42-199`
- Test: Existing callers compile without adding a change value.

**Interfaces:**
- Consumes: Existing dialog callers plus optional `change: Long = 0L`.
- Produces: Conditional change detail row for cash overpayment.

- [x] **Step 1: Add optional change parameter**

Add parameter at end of `PaymentConfirmationDialog` signature so existing callers remain source-compatible:

```kotlin
change: Long = 0L,
```

- [x] **Step 2: Render conditional change detail**

After the existing Method detail row, before Items, add:

```kotlin
if (change > 0L) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    )
    DetailRow(
        icon = Icons.Filled.Payments,
        label = "Change (Kembalian)",
        value = "Rp ${formatRupiah(change)}",
        emphasized = true,
    )
}
```

Extend `DetailRow` with `emphasized: Boolean = false` and use primary color for its value when emphasized; preserve existing row layout and all other callers.

- [x] **Step 3: Compile all dialog callers**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`; PaymentMethod and PaymentSplit callers compile with default `change = 0L`.

### Task 3: Build and Verify Payment States

**Files:**
- Verify: `PaymentCashScreen.kt`, `PaymentConfirmationDialog.kt`

- [x] **Step 1: Build and lint**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` and no new lint errors.

- [x] **Step 2: Install and launch API 25**

Run:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Expected: install `Success`; MainActivity `Status: ok`.

- [x] **Step 3: Verify underpayment, exact, and overpayment**

At `320dp` Payment Entry:

- Underpayment: Remaining is positive, no Change row, Complete Payment disabled.
- Exact payment: Remaining is `Rp 0`, no Change row, Complete Payment enabled.
- Overpayment: Remaining is `Rp 0`, Change row shows `cashReceived - grandTotal`, Complete Payment enabled, and confirmation dialog shows the same change.
- Confirming overpayment invokes `onConfirm` with the entered cash and calculated change.
