# Prototype Responsive Payment Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce oversized typography and spacing in Complete Payment dialog at 320dp while preserving wide-screen presentation and all dialog behavior.

**Architecture:** Keep dialog data and callbacks unchanged. Add `BoxWithConstraints` around the existing surface, derive compact/wide visual tokens, and pass a compact flag into the local detail row helper to control text size and label/value balance.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `BoxWithConstraints`, Gradle Android build, API 25 emulator.

## Global Constraints

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt`.
- Use width-based compact and wide visual tokens.
- Keep customer, method, change, total, item data, confirm callback, and back callback unchanged.
- Do not change production `app/` code or add dependencies.
- Do not commit or push unless separately authorized.

---

### Task 1: Make Confirmation Dialog Typography Responsive

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt:3-248`
- Test: Manual API 25 dialog screenshot; no relevant automated prototype test covers this composable.

**Interfaces:**
- Consumes: Existing dialog data, `change` value, `onConfirm`, and `onBack` callbacks.
- Produces: Same dialog behavior with width-adaptive visual tokens.

- [x] **Step 1: Add width constraint import and tokens**

Add `BoxWithConstraints` import and wrap the existing `Surface` inside it. Derive:

```kotlin
BoxWithConstraints {
    val isCompact = maxWidth < 360.dp
    val surfacePadding = if (isCompact) 16.dp else 24.dp
    val iconSize = if (isCompact) 40.dp else 48.dp
    val iconGlyphSize = if (isCompact) 24.dp else 28.dp
    val titleStyle = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
    val subtitleStyle = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val totalStyle = if (isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium
    val detailPadding = if (isCompact) 12.dp else 16.dp
    val actionHeight = 48.dp
    val actionTextStyle = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
    val actionIconSize = if (isCompact) 18.dp else 20.dp

    Surface(
        shape = RoundedCornerShape(if (isCompact) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        // Existing dialog content uses these tokens.
    }
}
```

Do not modify `total`, `customerName`, `methodDescription`, `itemCount`, `change`, `onConfirm`, or `onBack`.

- [x] **Step 2: Apply compact header, total, and spacing tokens**

Keep existing content order. Replace fixed values as follows:

```kotlin
Column(
    modifier = Modifier.padding(surfacePadding),
    verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 0.dp),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconGlyphSize),
            )
        }
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
        Text(
            text = "Complete this payment?",
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Please review the details before confirming.",
            style = subtitleStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TOTAL AMOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Rp ${formatRupiah(total)}", style = totalStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
```

Retain current wide spacing by keeping the existing explicit spacers where `isCompact` is false. The compact branch must not enlarge any typography beyond the token values above.

- [x] **Step 3: Compact detail card and action buttons**

Use `detailPadding` for the detail card column and pass `compact = isCompact` to every `DetailRow`:

```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(modifier = Modifier.padding(detailPadding)) {
        DetailRow(icon = Icons.Filled.Person, label = "Customer", value = customerName, compact = isCompact)
        HorizontalDivider(modifier = Modifier.padding(vertical = if (isCompact) 8.dp else 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        DetailRow(icon = Icons.Filled.Payments, label = "Method", value = methodDescription, compact = isCompact)
        if (change > 0L) {
            HorizontalDivider(modifier = Modifier.padding(vertical = if (isCompact) 8.dp else 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            DetailRow(icon = Icons.Filled.Payments, label = "Change", value = "Rp ${formatRupiah(change)}", compact = isCompact, emphasized = true)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = if (isCompact) 8.dp else 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        DetailRow(icon = Icons.Filled.ShoppingBag, label = "Items", value = "$itemCount items", compact = isCompact)
    }
}
```

Set both action buttons to `actionHeight`, use `actionTextStyle`, and set the confirm icon to `actionIconSize`. Keep button colors, labels, callbacks, and ordering unchanged. Use compact spacers of `8.dp` between actions and wide spacers of `12.dp`.

- [x] **Step 4: Balance detail row typography**

Change helper signature and use balanced compact weights:

```kotlin
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean = false,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(if (compact) 0.38f else 0.3f),
        )
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(if (compact) 0.62f else 0.7f),
            textAlign = TextAlign.End,
            softWrap = true,
        )
    }
}
```

Update all existing `DetailRow` calls with `compact = isCompact` inside the dialog. Preserve default values so no external helper callers are affected.

- [x] **Step 5: Compile and lint**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` and no new lint errors.

- [x] **Step 6: Install and verify API 25 dialog**

Install and launch:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Navigate to overpaid cash confirmation at `320dp` and verify title, subtitle, total, Customer, Method, Change, Items, Confirm Payment, and Back all fit without oversized text or clipping. Confirm the `Change` value and callbacks remain unchanged.
