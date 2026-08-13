# Prototype Adaptive UI Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a blue semantic visual foundation and adaptive Cashier workspace in `prototype/android-prototype` without changing existing prototype flow behavior.

**Architecture:** Keep Material 3 `ColorScheme` as the only visual-token interface. `PosShell` selects bottom navigation or a navigation rail from available width and passes unchanged scaffold padding to tab content. `CashierScreen` retains its local cart/customer state and checkout callback, selecting mobile-sheet versus persistent-cart presentation only from available content width.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation 3, Android Gradle Plugin 9, API 23+.

## Global Constraints

- Modify only `prototype/android-prototype`; do not change production `app/` or backend code.
- Primary action color is `#5F7DF7`; pressed/strong blue is `#4968EB`; primary container is `#E9EEFF`.
- Use neutral background `#F7F8FC`, white main surfaces, semantic green success, semantic red error/refund, and no amber/yellow branding.
- Preserve `Navigation.kt` routes, `SessionState`, cart calculation, customer selection, checkout callbacks, payment flow, and close/opening behavior.
- Keep minSdk 23 and add no dependencies.
- Use Material roles rather than per-screen `Color(...)` constants.
- Compact width is `<600dp`, medium is `600dp..839dp`, and expanded is `>=840dp`.
- Compact uses bottom navigation only; medium and expanded use navigation rail only.
- Do not commit or push without explicit user authorization.

## File Structure

- Modify `app/src/main/java/com/rotiropi/pos_prototype/theme/Color.kt`: semantic blue, neutral surface, success, error, and restrained warning tokens.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/theme/Theme.kt`: rename the internal scheme and retain one Material 3 theme entry point.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/theme/Type.kt`: keep readable existing Android typography and remove amber-oriented type naming only if present.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosShell.kt`: width-aware layout chrome and single navigation selection.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosBottomBar.kt`: compact navigation visual cleanup and a reusable rail implementation using `NavTab`.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`: adaptive catalog/cart arrangement while retaining all local state and callbacks.
- Modify `app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`: replace `Amber` copy with `Blue`.

---

### Task 1: Replace Amber Theme With Semantic Blue Tokens

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/theme/Color.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/theme/Theme.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt:135`

**Interfaces:**
- Consumes: existing top-level color names imported by `Theme.kt`.
- Produces: unchanged `POSPrototypeTheme(content: @Composable () -> Unit)` with a blue semantic `MaterialTheme.colorScheme` for every screen.

- [ ] **Step 1: Capture current amber references in the prototype source**

Run:

```bash
rg -n 'Amber|amber|0xFF7B5800|0xFFFEBA11|0xFFFFBB12' app/src/main
```

Expected: matches in `theme/Color.kt`, `theme/Theme.kt` scheme naming or comments, and More appearance copy.

- [ ] **Step 2: Replace the `Color.kt` token values with semantic blue and neutral values**

Keep the existing top-level identifiers so `Theme.kt` remains the only consumer-facing mapping. Set the roles as follows:

```kotlin
val Primary = Color(0xFF5F7DF7)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE9EEFF)
val OnPrimaryContainer = Color(0xFF2F4DBA)
val OnPrimaryFixedVariant = Color(0xFF4968EB)
val InversePrimary = Color(0xFF4968EB)

val Secondary = Color(0xFF4F9D8D)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDFF4EE)
val OnSecondaryContainer = Color(0xFF1E5F53)

val Tertiary = Color(0xFF8A6B3F)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFF7EEDB)
val OnTertiaryContainer = Color(0xFF5D451E)

val Background = Color(0xFFF7F8FC)
val OnBackground = Color(0xFF1D1D1F)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1D1D1F)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF2F4F8)
val SurfaceContainer = Color(0xFFEEF1F6)
val SurfaceContainerHigh = Color(0xFFEAEDF3)
val SurfaceContainerHighest = Color(0xFFE4E8EF)
val SurfaceVariant = Color(0xFFEEF1F6)
val OnSurfaceVariant = Color(0xFF6E6E73)
val SurfaceTint = Primary
val Outline = Color(0xFF9AA0A6)
val OutlineVariant = Color(0xFFE2E6EB)

val Error = Color(0xFFC84E5A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFDECEF)
val OnErrorContainer = Color(0xFF7D2631)
```

Keep inverse surface/on-inverse surface dark and readable. Do not add screen-specific colors.

- [ ] **Step 3: Rename the private warm scheme and use blue appearance copy**

In `Theme.kt`, rename only the private `WarmCommerceColors` value to
`PrototypeColorScheme` and change `POSPrototypeTheme` to pass it:

```kotlin
private val PrototypeColorScheme = lightColorScheme(/* existing role mapping */)

@Composable
fun POSPrototypeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PrototypeColorScheme,
        typography = Typography,
        shapes = WarmCommerceShapes,
        content = content,
    )
}
```

In `MoreScreen.kt`, retain the `MoreRow` and replace only visible copy:

```kotlin
MoreRow("Accent Color", "Blue", valueColor = MaterialTheme.colorScheme.primary)
```

- [ ] **Step 4: Compile and lint theme consumers**

Run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL`. Existing unrelated deprecation warnings may remain; no new compilation or lint failure.

- [ ] **Step 5: Re-scan for forbidden amber theme values**

Run:

```bash
rg -n 'Amber|amber|0xFF7B5800|0xFFFEBA11|0xFFFFBB12' app/src/main
```

Expected: no results except product name `Orange Juice` is unrelated and must not be changed.

### Task 2: Add Adaptive Navigation Chrome

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosShell.kt`
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosBottomBar.kt`

**Interfaces:**
- Consumes: `NavTab`, `PosTopBar()`, and `content: @Composable (PaddingValues) -> Unit`.
- Produces: unchanged `PosShell(activeTab, onTabSelected, modifier, content)` signature; compact bottom bar and wider navigation rail selected by width.

- [ ] **Step 1: Add a layout-mode helper in `PosShell.kt`**

Define this private enum and helper above `PosShell`:

```kotlin
private enum class PosLayoutMode { Compact, Medium, Expanded }

private fun posLayoutMode(maxWidth: Dp): PosLayoutMode = when {
    maxWidth < 600.dp -> PosLayoutMode.Compact
    maxWidth < 840.dp -> PosLayoutMode.Medium
    else -> PosLayoutMode.Expanded
}
```

Import `BoxWithConstraints`, `Row`, `Dp`, and `dp`. Do not expose the helper or change callers.

- [ ] **Step 2: Make `PosShell` choose one navigation component**

Wrap the scaffold in `BoxWithConstraints`, obtain `val layoutMode = posLayoutMode(maxWidth)`, and render:

```kotlin
if (layoutMode == PosLayoutMode.Compact) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PosTopBar() },
        bottomBar = { PosBottomBar(activeTab, onTabSelected) },
        content = content,
    )
} else {
    Row(modifier = modifier.fillMaxSize()) {
        PosNavigationRail(
            selectedTab = activeTab,
            onTabSelected = onTabSelected,
        )
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { PosTopBar() },
            content = content,
        )
    }
}
```

Use the same scaffold `PaddingValues` contract in both branches. Do not show a rail and bottom bar together.

- [ ] **Step 3: Add `PosNavigationRail` in `PosBottomBar.kt`**

Use Material 3 `NavigationRail` and `NavigationRailItem`. Reuse `NavTab.entries`
and provide both icon and label, preserving navigation accessibility semantics:

```kotlin
@Composable
fun PosNavigationRail(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(12.dp))
        NavTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
```

Add only required Material 3/layout imports. Keep `PosBottomBar` compact-only,
but reduce its `shadowElevation` to `0.dp` and use `outlineVariant` divider or
border if visual separation is needed.

- [ ] **Step 4: Build and inspect compact versus wide shell hierarchy**

Run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL`.

Install and launch:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Expected: app launches. Capture current compact screen using:

```bash
android screen capture -o "/var/folders/tw/3x4p8vcx40qgzvdfck1615mc0000gn/T/opencode/prototype-shell-compact.png"
```

Visually inspect PNG. It must show one navigation component only after entering
the authenticated prototype flow.

### Task 3: Make Cashier Grid and Cart Presentation Adaptive

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`

**Interfaces:**
- Consumes: unchanged `CashierScreen(onCheckout, onTabSelected, modifier)`, `SessionState`, `SelectCustomerSheet`, and cart `SnapshotStateMap<String, Int>`.
- Produces: same checkout arguments and customer/cart behavior, with adaptive catalog columns and cart presentation.

- [ ] **Step 1: Extract unchanged checkout state mutation into one lambda**

After computing `totalQty` and `subtotal`, define:

```kotlin
val checkout = {
    val tax = (subtotal * 0.10).toLong()
    SessionState.itemCount = totalQty
    SessionState.customerName = selectedCustomer?.name ?: "Walk-in Customer"
    onCheckout(subtotal.toDouble(), tax.toDouble(), (subtotal + tax).toDouble())
}
```

Replace the existing sheet inline checkout body with `showCart = false; checkout()`.
This must preserve tax arithmetic, assignments, and callback values exactly.

- [ ] **Step 2: Choose grid columns and cart mode from available Cashier width**

Inside the `PosShell` content lambda, wrap page content in `BoxWithConstraints`.
Define:

```kotlin
val expanded = maxWidth >= 840.dp
val productColumns = when {
    maxWidth < 600.dp -> 2
    maxWidth < 840.dp -> 3
    maxWidth < 1120.dp -> 4
    else -> 5
}
```

Replace `GridCells.Fixed(2)` with `GridCells.Fixed(productColumns)`. Preserve
product filtering, item key, add-to-cart action, content padding, and spacing.

- [ ] **Step 3: Split catalog content from cart presentation without duplicating behavior**

Create a private `CashierCatalog` composable containing the existing search,
category row, product `LazyVerticalGrid`, and compact View Cart button.

Use this exact interface:

```kotlin
@Composable
private fun CashierCatalog(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    products: List<Product>,
    productColumns: Int,
    cart: SnapshotStateMap<String, Int>,
    totalQty: Int,
    subtotal: Long,
    showCartButton: Boolean,
    onShowCart: () -> Unit,
    modifier: Modifier = Modifier,
)
```

When `showCartButton` is true and `totalQty > 0`, use a primary CTA:

```kotlin
colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
)
```

Set elevation to `0.dp`. Keep 52dp compact CTA height.

- [ ] **Step 4: Render persistent expanded cart panel**

Within `BoxWithConstraints`, use:

```kotlin
if (expanded) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CashierCatalog(
            modifier = Modifier.weight(1f),
            /* pass existing state and showCartButton = false */
        )
        Surface(
            modifier = Modifier.widthIn(min = 360.dp, max = 420.dp).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            CartSheetContent(
                cart = cart,
                customer = selectedCustomer,
                onSelectCustomer = { showSelectCustomer = true },
                onCheckout = checkout,
                modifier = Modifier.fillMaxSize(),
                expanded = true,
            )
        }
    }
} else {
    CashierCatalog(
        modifier = Modifier.fillMaxSize(),
        /* pass existing state and showCartButton = true */
    )
}
```

Update `CartSheetContent` to accept `modifier: Modifier = Modifier` and
`expanded: Boolean = false`. Its outer column uses `modifier.fillMaxWidth()`;
apply `.fillMaxHeight(0.85f)` only when `expanded` is false. Preserve rows,
quantity actions, remove actions, totals, and checkout callback. In expanded
mode, use 56dp checkout height and surface elevation `0.dp`.

- [ ] **Step 5: Keep customer-sheet return behavior mode-aware**

In `SelectCustomerSheet` callbacks, retain selection state. After selecting or
width-derived value captured in the composition. This preserves compact return
to the cart sheet while expanded returns to the visible cart panel.

- [ ] **Step 6: Use soft error container for low-stock badge and normalize card surface**

In `ProductCard`, replace the low-stock badge colors with:

```kotlin
background(if (lowStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
```

and:

```kotlin
color(if (lowStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
```

Set `Surface` shape to `RoundedCornerShape(16.dp)`, use
`shadowElevation = 0.dp`, and add a 1dp `outlineVariant` border. Preserve
product click, stock threshold (`<= 5`), quantity badge, price, text truncation,
and product data.

- [ ] **Step 7: Build and verify Cashier interactions**

Run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL`.

Install APK, enter the prototype flow, and verify all cases:

```text
Compact portrait: two columns, View Cart CTA, cart sheet, customer selector, checkout callback path.
Compact landscape: no clipped CTA or navigation.
Medium width: rail only, three catalog columns, cart remains modal.
Expanded width: rail only, four/five columns, persistent right cart, customer selector returns to cart panel, checkout CTA visible.
```

Take one screenshot for each available configured emulator/window size using
`android screen capture`; inspect every PNG before recording a pass.

### Task 4: Final Regression and Scope Review

**Files:**
- Inspect: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/**`
- Inspect: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/theme/**`

**Interfaces:**
- Consumes: all Task 1-3 source changes.
- Produces: verified phase-one foundation without changes outside the approved prototype scope.

- [ ] **Step 1: Run prototype unit tests and record known scaffold status**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: either `BUILD SUCCESSFUL` or the pre-existing unresolved legacy test
scaffold error in `MainScreenViewModelTest.kt`. Do not alter that stale test
scaffold as part of this UI plan; report it separately if still failing.

- [ ] **Step 2: Run required build and lint one final time**

Run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect scoped diff and hardcoded theme references**

Run:

```bash
git diff -- prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/theme prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more
rg -n 'Amber|amber|0xFF7B5800|0xFFFEBA11|0xFFFFBB12' prototype/android-prototype/app/src/main
```

Expected: diff stays within approved prototype UI/theme files. No amber branding
reference remains. Do not modify production `app/`, prototype data, navigation
keys, or session data.

- [ ] **Step 4: Do not commit or push**

Leave intended changes unstaged unless the user separately asks to commit.

## Spec Coverage Review

- Semantic blue, neutral surfaces, success/error, and no amber branding: Task 1.
- Shapes and typography remain centralized: Task 1 preserves theme shape/type entry points.
- Compact bottom navigation versus medium/expanded rail: Task 2.
- Adaptive grid, persistent expanded cart, mobile sheet behavior, CTA hierarchy, and stock semantics: Task 3.
- Build/lint, API 25 install, size checks, and scoped diff: Tasks 2-4.
- No backend/state/navigation behavior changes: global constraints and Task 3 checkout/customer steps.

## Plan Self-Review

- No placeholders, deferred implementation, or undefined helper interfaces remain.
- `PosShell` signature and `CashierScreen` callbacks remain unchanged.
- `CashierCatalog` owns only presentation; parent retains all state and checkout mutation.
- Existing stale unit-test scaffold is explicitly isolated from UI work.
