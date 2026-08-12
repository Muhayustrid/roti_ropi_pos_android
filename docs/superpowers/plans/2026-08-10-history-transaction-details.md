# Prototype History And Transaction Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make prototype History cards open reusable transaction-detail layouts for successful, refunded, draft, and error states, then add the More/close-shift flow represented by Stitch 3.6–3.7.

**Architecture:** Keep dummy transaction data local to the prototype. Add a typed `TransactionDetail` navigation key carrying only transaction ID; detail screen resolves the transaction from `DUMMY_TRANSACTIONS` and chooses the state-specific layout. Add a reusable `MoreScreen` and `ConfirmClosingSheet`; existing `PosShell` and `PosBottomBar` remain the shared chrome.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation 3, standalone `prototype/android-prototype`, emulator `mobile-pos-api36`.

## Global Constraints

- Modify only `prototype/android-prototype/`; do not touch production `app/`.
- Keep all data dummy; no backend/API work.
- Preserve existing History filters and Cashier/History navigation behavior.
- Use existing Compose, Material 3, icons, theme, and navigation dependencies.
- Do not commit or push without explicit user approval.
- Verify with `./gradlew :app:assembleDebug` from `prototype/android-prototype/`.

## Stitch Mapping

- 3.1: `5bcd8f48b4124f6a8a5dedd2c375eb6a` Detailed Transaction History - Mobile.
- 3.2: `5dc8f22ea9544fc28239cb1dde6f51c1` Transaction Detail - Successful Sale.
- 3.3: `49045ee61bac43d1a9531f86bbf8463d` Transaction Detail - Refunded State.
- 3.4: `72b772286893484b8248a8301d458206` Transaction Detail - Draft State.
- 3.5: `17bd9cba232e475e82954c8636cb7523` Transaction Detail - Error State.
- 3.6: `ce4aaa8984134cfd84007b00a7444526` More - Ready to Close Shift.
- 3.7: `bf9c3e5090904b349950b56fb28e4ff8` Confirm Closing - Modal Bottom Sheet.

---

### Task 1: Wire History Card Selection

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/history/HistoryScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/Navigation.kt`

**Interfaces:**
- `HistoryScreen` gains `onTransactionSelected: (String) -> Unit`.
- `TransactionCard` receives the same callback and calls it from its root card click.
- Navigation adds `data class TransactionDetail(val transactionId: String)` and maps it to `TransactionDetailScreen`.

- [ ] Add `onTransactionSelected` with default no-op only where source compatibility is needed; Navigation passes the real callback.
- [ ] Make each `TransactionCard` clickable without changing status-specific visuals or draft Resume behavior.
- [ ] Add `TransactionDetail` key beside existing Navigation keys and import `TransactionDetailScreen`.
- [ ] Add entry handling missing IDs by rendering the 3.5 error state.
- [ ] Compile with `./gradlew :app:assembleDebug`.

### Task 2: Build Transaction Detail States 3.2–3.5

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/history/TransactionDetailScreen.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/data/Transactions.kt` only if a lookup helper is needed.

**Interfaces:**

```kotlin
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onClose: () -> Unit,
    onPrint: () -> Unit = {},
    onStartReturn: () -> Unit = {},
    onResumeDraft: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

- [ ] Resolve the selected transaction from `DUMMY_TRANSACTIONS`; pass a nullable result into a state renderer.
- [ ] Implement successful layout: close/print header, drag handle, success badge, ID/date, customer summary, total paid, payment method, order items, grand total, Start Return and Close actions.
- [ ] Implement refunded layout: refunded badge, Refund Information card, refund amount/reason/method/original reference, returned items with undo markers, refund totals, Close action.
- [ ] Implement draft layout: Draft Order header, customer, warning note, order summary, totals, Back and Resume Order actions.
- [ ] Implement missing/error layout: broken-item illustration placeholder using existing initials/Material icon, error message, Try Again, Return to History.
- [ ] Keep all action callbacks local/no-op except navigation callbacks supplied by Navigation.
- [ ] Compile with `./gradlew :app:assembleDebug`.

### Task 3: Build More And Confirm Closing 3.6–3.7

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/ConfirmClosingSheet.kt`
- Modify: `prototype/android-prototype/Navigation.kt`

**Interfaces:**

```kotlin
@Composable
fun MoreScreen(
    onTabSelected: (NavTab) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun ConfirmClosingSheet(
    onConfirm: () -> Unit,
    onEditAmounts: () -> Unit,
    onDismiss: () -> Unit,
)
```

- [ ] Add More destination key and route it from Cashier/History `NavTab.More` callbacks.
- [ ] Render More - Ready to Close Shift: account/session card, Close Shift button, Appearance section, General section, Sign out, and shared bottom navigation with More selected.
- [ ] Open `ConfirmClosingSheet` from Close Shift; render expected/counted Cash and QRIS, totals, difference, Confirm & Close Shift, and Edit Amounts.
- [ ] Keep Confirm and Edit actions as prototype no-ops or close the sheet; do not add closing backend behavior.
- [ ] Compile with `./gradlew :app:assembleDebug`.

### Task 4: Device Verification And Evidence

**Files:**
- Evidence: `prototype/android-prototype/design-refs/history-verification/`

- [ ] Install `app/build/outputs/apk/debug/app-debug.apk` on `mobile-pos-api36`.
- [ ] Navigate to History and capture 3.1.
- [ ] Tap successful card `#TRX-9402`; capture 3.2 and verify detail texts/items/actions.
- [ ] Tap refunded and draft cards; capture 3.3 and 3.4.
- [ ] Navigate an unknown transaction ID or trigger missing detail; capture 3.5.
- [ ] Tap More; capture 3.6 and open Close Shift; capture 3.7.
- [ ] Verify Cashier ↔ History tabs still route and More opens More screen.
- [ ] Run `./gradlew :app:assembleDebug` and `git diff --check`.
- [ ] Do not commit.
