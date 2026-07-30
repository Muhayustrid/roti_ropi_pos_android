# Task 2D Static Cashier Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add honest static Compose surfaces for Cashier, bounded cart, disabled exact-settlement checkout, and terminal receipt without runtime sales integration.

**Architecture:** Feature-local immutable UI models drive stateless composables. `CashierScreen` selects compact bottom-sheet or expanded persistent-cart composition; focused checkout and receipt composables render only caller-supplied server-shaped labels. `PosShell` supplies an unavailable release state; synthetic content remains in debug previews and tests.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose BOM 2026.06.00, Material 3, Navigation Compose, JUnit 4, Compose UI tests, Android API 23–36.

## Global Constraints

- Preserve `minSdk 23`, `targetSdk 36`, and `com.rotiropi.pos_erpnext`.
- Add no dependency, endpoint, DTO, repository, ViewModel, mutation, recovery, or backend change.
- Keep synthetic records in debug/test sources and visibly label them `Demo data`.
- Release runtime renders an honest unavailable state.
- Bound cart visuals to 50 rows.
- Label price and stock as server snapshots.
- Keep confirmation disabled until authoritative payable and server payment modes exist.
- Do not calculate authoritative subtotal, tax, payable, or change.
- Do not add overpayment, editable discounts, camera, printer, or sync UI.
- Preserve 48 dp targets, TalkBack/keyboard order, and font scale 1.5 usability.
- Do not commit, push, or start Task 2E.

---

### Task 1: Define Static UI Contracts

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/CheckoutUiState.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/ReceiptContent.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CashierUiState.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/CashierStateTest.kt`

**Interfaces:**
- Produces: `CheckoutUiState`, `ReceiptContent`, `CashierUiState`, `CashierContent`, `CashierCategory`, `CashierProduct`, `CartLine`, `CartSnapshot.visibleLines`, `MAX_CART_ROWS`, `priceSnapshotLabel()`, `stockSnapshotLabel()`.
- Consumes: no runtime DTO or API type.

- [ ] **Step 1: Write failing state tests**

```kotlin
@Test
fun cart_visuals_are_bounded_to_fifty_rows() {
    val cart = CartSnapshot(
        lines = (1..55).map { CartLine("ITEM-$it", "Item $it", "$it", "1", "IDR 1,000", "Pack") },
        itemCountLabel = "55 items",
        payableLabel = "Demo total IDR 55,000",
    )
    assertEquals(50, cart.visibleLines.size)
}

@Test
fun cashier_product_labels_remain_non_authoritative() {
    val product = CashierProduct(
        "CROISSANT-PACK", "Croissant Pack", "pastry", "25,000", "IDR",
        "Outlet Retail", "18", "Pack", "Outlet 01 - RR",
    )
    assertEquals("IDR 25,000 · Outlet Retail server snapshot", product.priceSnapshotLabel())
    assertEquals("18 Pack · Outlet 01 - RR server stock snapshot", product.stockSnapshotLabel())
}

@Test
fun receipt_keeps_server_change_as_supplied() {
    val receipt = ReceiptContent(
        "SINV-0001", "Walk-in Customer", "IDR 55,000", "IDR 55,000", "IDR 0", "Paid", true,
    )
    assertEquals("IDR 0", receipt.changeAmount)
}
```

- [ ] **Step 2: Verify tests fail for missing symbols**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.CashierStateTest"
```

Expected: Kotlin compilation failure naming missing Task 2D types.

- [ ] **Step 3: Implement minimum immutable contracts**

```kotlin
const val MAX_CART_ROWS = 50

data class CartSnapshot(
    val lines: List<CartLine>,
    val itemCountLabel: String,
    val payableLabel: String,
) {
    val visibleLines = lines.take(MAX_CART_ROWS)
}

sealed interface CheckoutUiState {
    data object Unavailable : CheckoutUiState
    data object OfflineNotSubmitted : CheckoutUiState
    data class PriceChanged(val message: String) : CheckoutUiState
    data object Submitting : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}

sealed interface CashierUiState {
    data object Unavailable : CashierUiState
    data class Active(val content: CashierContent) : CashierUiState
    data class Receipt(val content: ReceiptContent) : CashierUiState
    data class Error(val message: String) : CashierUiState
}
```

`CashierContent` stores query, barcode, categories, selected category ID, products, cart, checkout state, and demo flag. Money stays formatted `String`; no arithmetic helper exists.

- [ ] **Step 4: Verify focused tests pass**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.CashierStateTest"
```

Expected: PASS.

---

### Task 2: Build Cashier, Cart, Checkout, and Receipt Surfaces

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/CheckoutPanel.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/ReceiptScreen.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CartContent.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/CashierScreen.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/CashierScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 models and `PosLayoutMode`.
- Produces: `CheckoutPanel`, `ReceiptScreen`, `CartContent`, and `CashierScreen`.

- [ ] **Step 1: Write failing release-honesty and browser tests**

Render `CashierUiState.Unavailable` and assert `Cashier unavailable` exists while `Demo data` does not. Render active test fixtures and assert:

```kotlin
composeRule.onNodeWithTag("cashier-search").assertIsDisplayed()
composeRule.onNodeWithTag("cashier-barcode").assertIsDisplayed()
composeRule.onNodeWithContentDescription("Cashier category Pastry").assertIsSelected()
composeRule.onNodeWithContentDescription("Add Croissant Pack to cart")
    .assertHasClickAction()
    .assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithText("IDR 25,000 · Outlet Retail server snapshot").assertIsDisplayed()
composeRule.onNodeWithText("18 Pack · Outlet 01 - RR server stock snapshot").assertIsDisplayed()
```

All fixtures live inside androidTest.

- [ ] **Step 2: Write failing adaptive-cart tests**

Compact closed: click `cashier-cart-summary` and assert callback. Compact open: assert `cashier-cart-sheet`. Expanded: assert `cashier-cart-pane` and absence of summary. Assert quantity tags `cart-decrease-CROISSANT-PACK` and `cart-increase-CROISSANT-PACK` are clickable and at least 48 dp.

- [ ] **Step 3: Write failing checkout and receipt tests**

For each `CheckoutUiState`, assert exact state copy and disabled `checkout-confirm`. `Error` and `PriceChanged` expose Retry; `Submitting` exposes progress. Assert `Overpayment`, `Change due`, `Discount`, and `Camera` are absent.

Receipt assertions:

```kotlin
composeRule.onNodeWithText("Receipt").assertIsDisplayed()
composeRule.onNodeWithText("SINV-0001").assertIsDisplayed()
composeRule.onNodeWithText("Server change").assertIsDisplayed()
composeRule.onNodeWithText("IDR 0").assertIsDisplayed()
composeRule.onNodeWithTag("receipt-close").assertHeightIsAtLeast(48.dp)
```

- [ ] **Step 4: Verify tests fail for missing composables**

```bash
./gradlew assembleDebugAndroidTest
```

Expected: Kotlin compilation failure naming Task 2D composables.

- [ ] **Step 5: Implement focused checkout and receipt composables**

`CheckoutPanel(state, modifier, onRetry)` renders caller-supplied state only. `checkout-confirm` remains disabled in every state. Errors and price changes use assertive live regions. `ReceiptScreen(content, modifier, onClose)` displays sale ID, customer, total, paid, status, and `Server change` exactly from `ReceiptContent`; it never parses money.

- [ ] **Step 6: Implement bounded cart**

`CartContent(cart, checkoutState, modifier, onDecreaseQuantity, onIncreaseQuantity, onRetry)` iterates only `cart.visibleLines`, shows caller-supplied quantity/price/item-count/payable labels, renders 48 dp quantity controls, then delegates to `CheckoutPanel`.

- [ ] **Step 7: Implement Cashier screen**

```kotlin
@Composable
fun CashierScreen(
    state: CashierUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    cartVisible: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onBarcodeChange: (String) -> Unit = {},
    onBarcodeSubmit: () -> Unit = {},
    onCategorySelected: (CashierCategory) -> Unit = {},
    onProductSelected: (CashierProduct) -> Unit = {},
    onOpenCart: () -> Unit = {},
    onDismissCart: () -> Unit = {},
    onDecreaseQuantity: (CartLine) -> Unit = {},
    onIncreaseQuantity: (CartLine) -> Unit = {},
    onRetry: () -> Unit = {},
    onCloseReceipt: () -> Unit = {},
)
```

Rules:

- `Unavailable`: honest explanation, no fixture.
- `Active`: heading, optional `Demo data`, search, manual/HID barcode with IME Done callback, category chips, adaptive product grid, snapshot labels.
- Compact: floating cart summary and `ModalBottomSheet` when `cartVisible`.
- Expanded: browser and fixed-width persistent cart pane.
- `Error`: assertive live region and Retry.
- `Receipt`: delegates to `ReceiptScreen`.

- [ ] **Step 8: Verify focused compilation and tests**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.CashierStateTest" assembleDebugAndroidTest
```

Expected: PASS.

---

### Task 3: Integrate Release Shell and Debug Previews

**Files:**
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt:20-103`
- Modify: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt:88-91`
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/CashierPreviews.kt`
- Test: `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReleaseFixtureExclusionTest.kt`

**Interfaces:**
- Consumes: `CashierScreen` and `CashierUiState.Unavailable`.
- Produces: Cashier route and previews `CashierCompactPreview`, `CashierCompactDarkPreview`, `CashierExpandedPreview`, `CashierFontScalePreview`, `CheckoutStatesPreview`, `ReceiptPreview`.

- [ ] **Step 1: Replace obsolete shell assertion**

Release state must stay unavailable, so shell test becomes:

```kotlin
@Test
fun cashier_release_destination_is_honest_and_has_no_input() {
    composeRule.onNodeWithTag("root-cashier").performClick()
    composeRule.onNodeWithText("Cashier unavailable").assertIsDisplayed()
    composeRule.onNodeWithText("Demo data").assertDoesNotExist()
    composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
}
```

Manual/HID input is covered by direct `CashierScreenTest` active fixtures.

- [ ] **Step 2: Integrate Cashier route**

```kotlin
composable(PosDestination.CASHIER.route) {
    CashierScreen(
        state = CashierUiState.Unavailable,
        layoutMode = layoutMode,
        modifier = Modifier.testTag("destination-content-cashier"),
    )
}
```

Keep Reports and More in placeholder loop.

- [ ] **Step 3: Add debug-only preview fixtures**

Every populated preview uses `demoData = true`, remains below 50 rows, and visibly renders `Demo data`. Cover compact sheet open, compact dark, expanded pane, font scale 1.5, checkout states, and receipt. Use existing `PosTheme`; add no dependency.

- [ ] **Step 4: Verify focused integration**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.CashierStateTest" --tests "com.rotiropi.pos_erpnext.ui.ReleaseFixtureExclusionTest" assembleDebugAndroidTest
```

Expected: PASS; preview-fixture exclusion remains green because previews live under `app/src/debug`.

---

### Task 4: Verify Accessibility and Required Gates

**Files:**
- Modify: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/CashierScreenTest.kt`
- Generate: `app/build/reports/mobile-pos-task2d/previews/*`
- Generate: `app/build/reports/mobile-pos-devices/api23/*`
- Generate: `app/build/reports/mobile-pos-devices/api36/*`

**Interfaces:**
- Consumes: complete Task 2D surfaces.
- Produces: unit, lint, build, preview, accessibility, keyboard/scanner, API 23, and API 36 evidence.

- [ ] **Step 1: Add accessibility/input checks**

Verify 48 dp product/quantity actions, selected category semantics, assertive checkout errors, scroll to `checkout-confirm` at font scale 1.5, logical focus order, barcode keyboard text plus IME callback, and absence of camera controls.

- [ ] **Step 2: Run full available Gradle gate**

```bash
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest compileReleaseKotlin
```

Expected: exit 0. Record lint warnings. Record `testReleaseUnitTest` as unavailable, not passing.

- [ ] **Step 3: Render and inspect previews**

Use `android studio check`, then `android studio render-compose-preview --print-semantics` for all six preview functions. Store artifacts under `app/build/reports/mobile-pos-task2d/previews/`. Inspect light/dark, compact/expanded, font 1.5, demo labels, disabled confirmation, server snapshots, receipt server change, and absence of unsupported controls.

- [ ] **Step 4: Run API 23 suite**

```bash
./tools/run-device-tests.sh api23
```

Expected: exit 0 and evidence under `app/build/reports/mobile-pos-devices/api23/`.

- [ ] **Step 5: Run API 36 suite**

```bash
./tools/run-device-tests.sh api36
```

Expected: exit 0 and evidence under `app/build/reports/mobile-pos-devices/api36/`.

- [ ] **Step 6: Review diff and stop**

```bash
git diff --check
git status --short
git diff --stat
git diff -- app/src/main app/src/debug app/src/test app/src/androidTest docs/superpowers
```

Require no dependency, backend, API, DTO, repository, ViewModel, local accounting, camera, printer, sync, or Task 2E change. Report every skipped or failed gate. Do not commit or push.
