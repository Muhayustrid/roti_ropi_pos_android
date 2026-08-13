# Cart and Return Stepper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Cashier cart and Return item quantity controls share one clean, touch-friendly stepper and fix row spacing/clipping.

**Architecture:** Extract `QuantityStepper` into existing prototype components and use it from `CartItemRow` and `ReturnLineCard`. Cart keeps its remove-at-one behavior; Return keeps every row and clamps quantity between zero and original quantity. No image assets or new dependency.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing prototype theme and Gradle setup.

## Global Constraints

- Change only `prototype/android-prototype/`.
- Use existing product placeholder initials; add no image assets or dependency.
- Keep checkout, payment totals, return totals, navigation, backend, and accounting behavior unchanged.
- Cashier cart decrease at quantity one removes item; Return decrease at zero keeps row visible.
- Stepper controls have 48dp minimum touch targets.
- Cart and Return content remain vertically scrollable.
- Do not commit or push without explicit user approval.

---

### Task 1: Create Shared Quantity Stepper

**Files:**
- Create: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/QuantityStepper.kt`

**Interfaces:**
- Produces `QuantityStepper(quantity, onDecrease, onIncrease, min, max, modifier)` for Cashier and Return.

- [ ] **Step 1: Implement the reusable component**

Create this component:

```kotlin
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = quantity > min,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
            }
            Text(
                quantity.toString(),
                modifier = Modifier.widthIn(min = 32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onIncrease,
                enabled = quantity < max,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
            }
        }
    }
}
```

Use existing Material icons and theme colors. Do not add a text field or image
loading behavior.

- [ ] **Step 2: Compile the component**

Run from `prototype/android-prototype/`:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Repair Cashier Cart Rows

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt:510-609`

**Interfaces:**
- `CartItemRow` keeps `product`, `qty`, `onInc`, `onDec`, and `onRemove` signatures.
- `CartSheetContent` keeps subtotal, tax, grand total, customer selection, and checkout callbacks.

- [ ] **Step 1: Replace inline stepper with shared component**

Remove the current inline `Row` containing 28dp `IconButton`s and render:

```kotlin
QuantityStepper(
    quantity = qty,
    onDecrease = onDec,
    onIncrease = onInc,
    min = 1,
    modifier = Modifier.widthIn(min = 136.dp),
)
```

Keep `onDec` behavior from `CartSheetContent`: remove product when `qty <= 1`,
otherwise decrement. Keep `onInc` increment behavior.

- [ ] **Step 2: Stabilize row layout**

Keep 64dp placeholder thumbnail. Use a flexible middle column and trailing
column so long names cannot push totals off-screen:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.Top,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            product.name.take(2).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$qty x Rp ${formatRupiah(product.price)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.clickable(onClick = onRemove),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
        Text("Rp ${formatRupiah(product.price * qty)}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        QuantityStepper(quantity = qty, onDecrease = onDec, onIncrease = onInc, min = 1)
    }
}
```

Keep the existing placeholder and Remove icon implementation; only move them
into the stable middle column. Keep cart list separators and total surface.

- [ ] **Step 3: Compile Cashier cart**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Match Return Items to Cart Interaction

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/returning/ReturnScreen.kt:45-310`

**Interfaces:**
- `ReturnScreen` retains transaction fixture, reason, refund mode, submit, and success navigation callbacks.
- `ReturnLine.quantity` becomes `Int` and is clamped by shared stepper bounds.

- [ ] **Step 1: Change ReturnLine quantity to integer state**

Use this model and defaults:

```kotlin
private data class ReturnLine(
    val name: String,
    val originalQuantity: Int,
    val unitPrice: Long,
    val quantity: Int,
)

ReturnLine("Roti Manis", 2, 12_000L, 1)
ReturnLine("Croissant", 3, 18_500L, 0)
ReturnLine("Pain au Chocolat", 2, 22_000L, 0)
```

Calculate refund total with `line.quantity * line.unitPrice` and selected item
count with `line.quantity > 0`.

- [ ] **Step 2: Replace Return Quantity text field**

Inside each `ReturnLineCard`, remove `OutlinedTextField` and use:

```kotlin
QuantityStepper(
    quantity = line.quantity,
    onDecrease = { onQuantityChange((line.quantity - 1).coerceAtLeast(0)) },
    onIncrease = { onQuantityChange((line.quantity + 1).coerceAtMost(line.originalQuantity)) },
    min = 0,
    max = line.originalQuantity,
    modifier = Modifier.align(Alignment.End),
)
```

Update the callback type to `(Int) -> Unit`. Quantity zero keeps card visible;
quantity cannot exceed original quantity. Keep line refund amount and all
reason/refund mode/summary cards unchanged.

- [ ] **Step 3: Compile Return screen**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Build and Emulator Verification

**Files:**
- Create directory: `prototype/design-refs/cart-return-verification/`
- Create screenshots: `01-cashier-cart.png`, `02-return-sale-stepper.png`

**Interfaces:**
- Uses existing Cashier and History/Return routes.
- Produces build/lint output, UI dumps, and layout screenshots.

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

- [ ] **Step 3: Verify Cashier cart**

Add Roti Manis and Croissant, open `Your Cart`, then verify:

1. Each row has stable thumbnail, name/price, Remove action, line total, and pill stepper.
2. Plus increments quantity and totals.
3. Minus decrements; quantity one removes item.
4. Subtotal, tax, grand total, and Continue to Payment remain correct.

- [ ] **Step 4: Verify Return stepper**

Open successful `#TRX-9402`, tap Start Return, then verify:

1. Rows start at `1/0/0` and use the same pill stepper.
2. Minus from zero keeps row visible; plus stops at original quantity.
3. Refund total and selected item count update after each step.
4. Reason, refund mode, Submit Return, and success navigation still work.

- [ ] **Step 5: Capture evidence and inspect semantics**

Capture the two named screenshots. For each screen run:

```text
adb shell uiautomator dump /sdcard/cart-return-ui.xml
adb pull /sdcard/cart-return-ui.xml /tmp/cart-return-ui.xml
```

Confirm no clipping, accessible stepper labels, visible totals, and no system-bar overlap.

- [ ] **Step 6: Inspect intended worktree**

Run:

```text
git diff --check
git status --short -- prototype docs/superpowers/specs/2026-08-10-cart-return-stepper-design.md docs/superpowers/plans/2026-08-10-cart-return-stepper.md
```

Expected: changes are limited to shared stepper, Cashier cart, Return screen,
approved spec/plan, and screenshots. Do not revert unrelated worktree changes.
