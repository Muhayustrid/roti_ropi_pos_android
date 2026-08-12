# Prototype Responsive Cart Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the cart summary footer scale its padding, spacing, typography, and checkout CTA by available width.

**Architecture:** Keep responsive decisions local to the existing cart footer in `CartSheetContent`. Wrap the footer content in `BoxWithConstraints`, derive compact, regular, and wide tokens from `maxWidth`, and apply those tokens without changing cart calculations or item layout.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `BoxWithConstraints`, Gradle Android build, API 25 emulator.

## Global Constraints

- Change the cart summary footer in `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`.
- Use width-based layout tokens for compact, regular, and wide content widths.
- Keep item totals, tax calculation, Grand Total calculation, and checkout behavior unchanged.
- Do not change production `app/` code or add dependencies.
- Do not commit or push unless separately authorized.

---

### Task 1: Implement Width-Based Cart Summary Tokens

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt:489-590`
- Test: Manual API 25 cart screenshot; no relevant automated prototype test covers this composable branch.

**Interfaces:**
- Consumes: Existing `subtotal`, `tax`, `grandTotal`, `formatRupiah`, `PriceDisplay`, and checkout callback.
- Produces: Same summary values and checkout behavior with width-dependent visual tokens.

- [x] **Step 1: Replace fixed footer dimensions with width tokens**

Keep the existing `Surface` and replace only its content with this width-aware structure:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val isCompact = maxWidth < 360.dp
    val isWide = maxWidth >= 600.dp
    val summaryPadding = if (isCompact) 12.dp else 16.dp
    val summaryHorizontalPadding = if (isCompact) 4.dp else 8.dp
    val summarySpacing = when {
        isCompact -> 8.dp
        isWide -> 16.dp
        else -> 12.dp
    }
    val rowSpacing = if (isCompact) 4.dp else 8.dp
    val summaryLabelStyle = if (isCompact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val summaryValueStyle = if (isCompact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.titleMedium
    }
    val totalLabelStyle = when {
        isCompact -> MaterialTheme.typography.titleMedium
        isWide -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    val totalAmountStyle = when {
        isCompact -> PriceDisplay.copy(fontSize = 24.sp, lineHeight = 30.sp)
        isWide -> PriceDisplay
        else -> PriceDisplay.copy(fontSize = 30.sp, lineHeight = 38.sp)
    }
    val checkoutHeight = when {
        isCompact -> 48.dp
        isWide -> 56.dp
        else -> 52.dp
    }

    Column(
        modifier = Modifier.padding(summaryPadding),
        verticalArrangement = Arrangement.spacedBy(summarySpacing),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = summaryHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", style = summaryLabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rp ${formatRupiah(subtotal)}", style = summaryValueStyle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Tax (10%)", style = summaryLabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rp ${formatRupiah(tax)}", style = summaryValueStyle)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (isCompact) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Grand Total", style = totalLabelStyle, fontWeight = FontWeight.Bold)
                    Text(
                        "Rp ${formatRupiah(grandTotal)}",
                        modifier = Modifier.fillMaxWidth(),
                        style = totalAmountStyle,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isWide) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Grand Total", style = totalLabelStyle, fontWeight = FontWeight.Bold)
                    Text("Rp ${formatRupiah(grandTotal)}", style = totalAmountStyle, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Button(
            onClick = onCheckout,
            enabled = entries.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(checkoutHeight),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Continue to Payment",
                    style = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
```

Do not change subtotal/tax/grand-total expressions, `onCheckout`, button colors, or the customer/item sections.

- [x] **Step 2: Compile and lint**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` for all tasks and no new lint errors.

- [x] **Step 3: Install and launch API 25 build**

Run:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Expected: install reports `Success` and `MainActivity` reports `Status: ok`.

- [x] **Step 4: Verify narrow cart appearance**

Open cart on `mobile-pos-api25` at `320dp` and confirm:

- Subtotal and tax rows use compact typography and spacing.
- Grand Total amount is visibly smaller than the previous `32.sp` value.
- Grand Total remains readable and does not overlap the divider or CTA.
- Checkout CTA fits within the sheet and uses compact height.
- Calculated values remain unchanged.

- [x] **Step 5: Review final scope**

Confirm source review shows only the cart summary footer changed; no production `app/` files, dependencies, item calculations, or checkout behavior changed.
