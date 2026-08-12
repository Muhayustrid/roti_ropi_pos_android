# Prototype Responsive Payment Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cash payment entry readable at narrow widths by adapting typography, quick-amount rows, keypad sizing, scrolling, and the completion CTA.

**Architecture:** Keep all payment behavior inside `PaymentCashScreen` and derive visual tokens from `BoxWithConstraints.maxWidth`. Keep Complete Payment outside the scrollable content so it remains accessible; change only layout and typography, not input or confirmation logic.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `BoxWithConstraints`, `verticalScroll`, Gradle Android build, API 25 emulator.

## Global Constraints

- Change `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt`.
- Use width-based layout tokens for compact, regular, and wide content widths.
- Keep cash input parsing, remaining amount, exact-payment validation, overpayment message, and confirmation dialog behavior unchanged.
- Do not change production `app/` code or add dependencies.
- Do not commit or push unless separately authorized.

---

### Task 1: Implement Responsive Payment Entry Layout

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt:3-288`
- Test: Manual API 25 payment screenshot; no relevant automated prototype test covers this composable.

**Interfaces:**
- Consumes: Existing `grandTotal`, `itemCount`, `onConfirm`, `onBack`, `input`, and payment validation expressions.
- Produces: Same payment callbacks and values with width-adaptive layout.

- [x] **Step 1: Add only layout imports**

Add these imports without adding dependencies:

```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
```

- [x] **Step 2: Add width tokens and keep the top bar single-line**

Wrap the screen content in a `BoxWithConstraints` so the same tokens are available to the top bar, scrollable content, and CTA. Derive these values before rendering content:

```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
) {
    val isCompact = maxWidth < 360.dp
    val isWide = maxWidth >= 600.dp
    val contentPadding = if (isCompact) 12.dp else 16.dp
    val contentSpacing = when {
        isCompact -> 8.dp
        isWide -> 12.dp
        else -> 10.dp
    }
    val quickWrap = maxWidth < 420.dp
    val quickRows = if (quickWrap) quickAmounts.chunked(3) else listOf(quickAmounts)
    val amountStyle = if (isCompact) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.headlineLarge
    }
    val keypadStyle = if (isCompact) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val keypadHeight = if (isCompact) 48.dp else 52.dp
    val buttonHeight = when {
        isCompact -> 48.dp
        isWide -> 56.dp
        else -> 52.dp
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Existing top bar, scrollable content, and Complete Payment CTA stay here.
    }
}
```

Move the existing top bar, content column, and CTA inside the inner `Column`. The content column uses `verticalScroll(rememberScrollState())`, `contentPadding`, and `contentSpacing`. Set top bar title style to `titleMedium` when `isCompact` and `titleLarge` otherwise. Add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to `Payment Entry - Cash`. Keep back navigation and trailing title spacer unchanged.

- [x] **Step 3: Make summary and received amount responsive**

Pass `compact = isCompact` to both `SummaryRow` calls. Use compact summary padding of `12.dp` and regular/wide padding of `16.dp`; use a `6.dp` compact spacer between rows and the existing `8.dp` otherwise.

Use the existing amount row with these visual values:

```kotlin
Text(
    text = "Amount Received (Uang Diterima)",
    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = "Rp ${formatRupiah(cashReceived)}",
        style = amountStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    IconButton(onClick = { if (input.isNotEmpty()) input = input.dropLast(1) }) {
        Icon(
            imageVector = Icons.Filled.Backspace,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Keep the existing overpayment message and condition unchanged.

- [x] **Step 4: Wrap quick amounts and shrink keypad compactly**

Render `quickRows` as rows of three at narrow widths, filling incomplete rows with weighted spacers so buttons retain equal widths:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    quickRows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { label ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (isCompact) 48.dp else 0.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            input = when (label) {
                                "Uang Pas" -> grandTotal.toString()
                                else -> (label.dropLast(1).toLong() * 1_000L).toString()
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = if (isCompact) 8.dp else 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (row.size < 3) {
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
```

Keep the existing `keys` values and click handling. Replace only keypad button height with `keypadHeight` and numeric text style with `keypadStyle`.

- [x] **Step 5: Make Complete Payment CTA responsive**

Keep `onClick`, `enabled = isExact`, colors, and label unchanged. Use:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(buttonHeight)
    .padding(horizontal = if (isCompact) 12.dp else 16.dp)
```

Use `labelLarge` for compact button text and `titleMedium` otherwise. Keep the existing bottom spacer, reducing it to `12.dp` only for compact mode.

- [x] **Step 6: Update SummaryRow typography without changing values**

Change helper signature and styles:

```kotlin
private fun SummaryRow(label: String, value: String, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

Do not alter any amount expression, input limit, validation condition, dialog, or callback.

- [x] **Step 7: Compile and lint**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` for all tasks and no new lint errors.

- [x] **Step 8: Install and launch API 25 build**

Run:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Expected: install reports `Success`; `MainActivity` reports `Status: ok`.

- [x] **Step 9: Verify payment screen**

Navigate to cash payment on `mobile-pos-api25` at `320dp` and confirm:

- Title remains one line.
- Summary values fit within the card.
- `Uang Pas`, `10k`, and `20k` occupy first quick row; `50k` and `100k` occupy second row without clipping.
- Keypad buttons and labels fit without overlap.
- Complete Payment CTA remains visible and enabled only for exact amount.
- Existing cash input, remaining amount, overpayment message, and confirmation flow still behave as before.
