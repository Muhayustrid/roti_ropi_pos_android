# Task 2E Reports and More Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add honest Reports and More Compose surfaces plus persisted theme mode/accent without report, bootstrap, printer, or synchronization runtime integration.

**Architecture:** Feature-local immutable models drive stateless `ReportsScreen` and `MoreScreen` composables. `ThemePreferences` stores two enum names in application-private `SharedPreferences`; `MainActivity` owns root theme state and `PosShell` passes controlled selections to More. Release navigation supplies unavailable report/outlet/user data, while populated fixtures remain in debug and test sources.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose BOM 2026.06.00, Material 3, Navigation Compose, Android `SharedPreferences`, JUnit 4, Compose UI tests, Android API 23–36.

## Global Constraints

- Preserve `minSdk 23`, `targetSdk 36`, and `com.rotiropi.pos_erpnext`.
- Add no dependency, endpoint, DTO, repository, ViewModel, authentication, bootstrap, mutation, recovery, printer, synchronization, camera, or backend change.
- Keep synthetic report, outlet, and user records in debug/test sources and visibly label populated fixtures `Demo data`.
- Release Reports renders `Reports unavailable`; never derive aggregates from bounded or paginated endpoints.
- Release More renders outlet and user/session as `Unavailable`.
- Printer and synchronization remain visible, disabled, and labeled `Not supported`.
- Keep KPI, breakdown, chart-summary, chart-value, and top-product values as caller-provided strings; perform no money parsing or business aggregation.
- Clamp normalized chart fractions only for safe Canvas geometry.
- Use application-private `SharedPreferences` for `System`/`Light`/`Dark` and existing `PosAccent.BLUE`/`PosAccent.TEAL`.
- Preserve 48 dp targets, selected/disabled semantics, text alternatives, TalkBack/keyboard order, and font scale 1.5 usability.
- Use representative previews to cover light/dark, both accents, phone/tablet, portrait/landscape, and font scale 1.5 without a Cartesian preview matrix.
- Do not commit, push, switch branch, or start Task 3 during this plan unless the user separately authorizes git actions.

---

### Task 1: Define Reports, More, and Theme Preference Contracts

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/reports/ReportsUiState.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/MoreUiState.kt`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/ThemePreferences.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReportsMoreStateTest.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ThemePreferencesInstrumentedTest.kt`

**Interfaces:**
- Consumes: existing `PosAccent` from `ui/theme/Color.kt` and Android `SharedPreferences`.
- Produces: `ReportPeriod`, `ReportMetric`, `ReportBreakdown`, `ReportChartBar.safeFraction`, `ReportTopProduct`, `ReportsContent`, `ReportsUiState`, `PosThemeMode`, `ThemeSelection`, `MoreUiState`, and `ThemePreferences`.

- [ ] **Step 1: Write failing pure-state tests**

Create `ReportsMoreStateTest.kt` with these behaviors and fixture values:

```kotlin
package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.reports.ReportBreakdown
import com.rotiropi.pos_erpnext.ui.reports.ReportChartBar
import com.rotiropi.pos_erpnext.ui.reports.ReportMetric
import com.rotiropi.pos_erpnext.ui.reports.ReportPeriod
import com.rotiropi.pos_erpnext.ui.reports.ReportTopProduct
import com.rotiropi.pos_erpnext.ui.reports.ReportsContent
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsMoreStateTest {

    @Test
    fun reports_keep_caller_supplied_labels_without_aggregation() {
        val content = ReportsContent(
            selectedPeriod = ReportPeriod.WEEK,
            metrics = listOf(ReportMetric("sales", "Net sales", "IDR 825,000", "Demo snapshot")),
            breakdown = listOf(ReportBreakdown("cash", "Cash", "IDR 525,000")),
            chartBars = listOf(ReportChartBar("fri", "Fri", "IDR 210,000", 0.75f)),
            chartSummary = "Peak sales on Friday.",
            topProducts = listOf(ReportTopProduct("croissant", "Croissant Pack", "18 sold")),
            demoData = true,
        )

        assertEquals("IDR 825,000", content.metrics.single().value)
        assertEquals("IDR 525,000", content.breakdown.single().value)
        assertEquals("Peak sales on Friday.", content.chartSummary)
        assertEquals("18 sold", content.topProducts.single().value)
    }

    @Test
    fun chart_fraction_is_clamped_only_for_safe_geometry() {
        assertEquals(0f, ReportChartBar("low", "Low", "0", -0.5f).safeFraction)
        assertEquals(0.4f, ReportChartBar("mid", "Mid", "40", 0.4f).safeFraction)
        assertEquals(1f, ReportChartBar("high", "High", "100", 1.5f).safeFraction)
        assertEquals(0f, ReportChartBar("invalid", "Invalid", "—", Float.NaN).safeFraction)
    }

    @Test
    fun unknown_theme_preferences_use_safe_defaults() {
        assertEquals(PosThemeMode.SYSTEM, ThemePreferences.parseThemeMode(null))
        assertEquals(PosThemeMode.SYSTEM, ThemePreferences.parseThemeMode("BROKEN"))
        assertEquals(PosAccent.BLUE, ThemePreferences.parseAccent(null))
        assertEquals(PosAccent.BLUE, ThemePreferences.parseAccent("ORANGE"))
    }

    @Test
    fun valid_theme_preferences_parse_enum_names() {
        assertEquals(PosThemeMode.DARK, ThemePreferences.parseThemeMode("DARK"))
        assertEquals(PosAccent.TEAL, ThemePreferences.parseAccent("TEAL"))
    }
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.ReportsMoreStateTest"
```

Expected: Kotlin test compilation fails because Task 2E report/settings symbols do not exist. Fix only test syntax/import mistakes until failure names missing production symbols.

- [ ] **Step 3: Write failing real SharedPreferences tests**

Create `ThemePreferencesInstrumentedTest.kt` before `ThemePreferences.kt`, using a test-only preference file:

```kotlin
@RunWith(AndroidJUnit4::class)
class ThemePreferencesInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(
        "theme_preferences_instrumented_test",
        Context.MODE_PRIVATE,
    )

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @After
    fun clearPreferencesAfterTest() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selections_round_trip_through_application_private_preferences() {
        val store = ThemePreferences(preferences)
        store.writeMode(PosThemeMode.DARK)
        store.writeAccent(PosAccent.TEAL)

        assertEquals(ThemeSelection(PosThemeMode.DARK, PosAccent.TEAL), store.read())
    }

    @Test
    fun corrupt_values_read_as_safe_defaults() {
        preferences.edit()
            .putString("theme_mode", "BROKEN")
            .putString("accent", "ORANGE")
            .commit()

        assertEquals(ThemeSelection(), ThemePreferences(preferences).read())
    }
}
```

- [ ] **Step 4: Compile instrumentation tests and verify RED**

```bash
./gradlew assembleDebugAndroidTest
```

Expected: Kotlin test compilation fails because `ThemePreferences`, `ThemeSelection`, and `PosThemeMode` do not exist. Fix only test harness/import errors until missing production symbols are the reason.

- [ ] **Step 5: Add immutable Reports contracts**

Create `ReportsUiState.kt` with exact contracts:

```kotlin
package com.rotiropi.pos_erpnext.ui.reports

enum class ReportPeriod(val label: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month"),
}

data class ReportMetric(
    val id: String,
    val label: String,
    val value: String,
    val supportingLabel: String,
)

data class ReportBreakdown(
    val id: String,
    val label: String,
    val value: String,
)

data class ReportChartBar(
    val id: String,
    val label: String,
    val valueLabel: String,
    val fraction: Float,
) {
    val safeFraction: Float = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
}

data class ReportTopProduct(
    val id: String,
    val label: String,
    val value: String,
)

data class ReportsContent(
    val selectedPeriod: ReportPeriod,
    val metrics: List<ReportMetric>,
    val breakdown: List<ReportBreakdown>,
    val chartBars: List<ReportChartBar>,
    val chartSummary: String,
    val topProducts: List<ReportTopProduct>,
    val demoData: Boolean,
)

sealed interface ReportsUiState {
    data object Unavailable : ReportsUiState
    data class Content(val content: ReportsContent) : ReportsUiState
}
```

- [ ] **Step 6: Add More and theme contracts**

Create `MoreUiState.kt`:

```kotlin
package com.rotiropi.pos_erpnext.ui.settings

import com.rotiropi.pos_erpnext.ui.theme.PosAccent

enum class PosThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class ThemeSelection(
    val mode: PosThemeMode = PosThemeMode.SYSTEM,
    val accent: PosAccent = PosAccent.BLUE,
)

data class MoreUiState(
    val outletLabel: String?,
    val userSessionLabel: String?,
    val themeMode: PosThemeMode,
    val accent: PosAccent,
    val demoData: Boolean,
)
```

Create `ThemePreferences.kt`:

```kotlin
package com.rotiropi.pos_erpnext.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent

class ThemePreferences(private val preferences: SharedPreferences) {

    fun read(): ThemeSelection = ThemeSelection(
        mode = parseThemeMode(preferences.getString(KEY_THEME_MODE, null)),
        accent = parseAccent(preferences.getString(KEY_ACCENT, null)),
    )

    fun writeMode(mode: PosThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun writeAccent(accent: PosAccent) {
        preferences.edit().putString(KEY_ACCENT, accent.name).apply()
    }

    companion object {
        internal const val FILE_NAME = "pos_ui_preferences"
        internal const val KEY_THEME_MODE = "theme_mode"
        internal const val KEY_ACCENT = "accent"

        fun from(context: Context): ThemePreferences = ThemePreferences(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        )

        internal fun parseThemeMode(value: String?): PosThemeMode =
            PosThemeMode.entries.firstOrNull { it.name == value } ?: PosThemeMode.SYSTEM

        internal fun parseAccent(value: String?): PosAccent =
            PosAccent.entries.firstOrNull { it.name == value } ?: PosAccent.BLUE
    }
}
```

No preference clear/reset API belongs in production; instrumentation tests clear their private test file directly.

- [ ] **Step 7: Run focused unit and persistence tests and verify GREEN**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.ReportsMoreStateTest" assembleDebugAndroidTest
./tools/run-device-tests.sh api36
```

Expected: all four unit tests pass and the complete API 36 suite ends with `OK (27 tests)`: baseline 25 plus two `ThemePreferencesInstrumentedTest` tests. Do not add formatting, aggregation, reset APIs, or storage abstractions.

---

### Task 2: Build Reports Surface

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/reports/ReportsScreen.kt`
- Create: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ReportsMoreScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 report models, `PosLayoutMode`, Material 3, and Compose `Canvas`.
- Produces: `ReportsScreen(state, layoutMode, modifier, onPeriodSelected)` plus stable tags `reports-compact`, `reports-expanded`, `reports-period-{today|week|month}`, and `reports-chart`.

- [ ] **Step 1: Write failing release-honesty test**

Start `ReportsMoreScreenTest.kt` with `createComposeRule()` and this test:

```kotlin
@Test
fun release_reports_is_honest_and_has_no_populated_controls() {
    composeRule.setContent {
        PosTheme {
            ReportsScreen(ReportsUiState.Unavailable, PosLayoutMode.COMPACT)
        }
    }

    composeRule.onNodeWithText("Reports unavailable").assertIsDisplayed()
    composeRule.onNodeWithText("Demo data").assertDoesNotExist()
    composeRule.onNodeWithText("Today").assertDoesNotExist()
    composeRule.onNodeWithText("Retry").assertDoesNotExist()
    composeRule.onNodeWithTag("reports-chart").assertDoesNotExist()
}
```

- [ ] **Step 2: Write failing populated Reports semantics test**

Add a `reportsFixture()` helper containing three periods implicitly through `ReportPeriod.entries`, two KPI metrics, two breakdown rows, three chart bars, summary `Peak sales on Friday.`, and two top products. Add:

```kotlin
@Test
fun populated_reports_exposes_demo_labels_period_and_chart_alternative() {
    var selected: ReportPeriod? = null
    composeRule.setContent {
        PosTheme {
            ReportsScreen(
                state = reportsFixture(),
                layoutMode = PosLayoutMode.COMPACT,
                onPeriodSelected = { selected = it },
            )
        }
    }

    composeRule.onNodeWithText("Demo data").assertIsDisplayed()
    composeRule.onNodeWithTag("reports-period-week")
        .assertIsSelected()
        .assertHasClickAction()
        .assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithTag("reports-period-month").performClick()
    composeRule.runOnIdle { assertEquals(ReportPeriod.MONTH, selected) }
    composeRule.onNodeWithText("IDR 825,000").assertIsDisplayed()
    composeRule.onNodeWithText("IDR 525,000").assertIsDisplayed()
    composeRule.onNodeWithText("18 sold").assertIsDisplayed()
    composeRule.onNodeWithText("Peak sales on Friday.").assertIsDisplayed()
    composeRule.onNodeWithContentDescription(
        "Sales trend chart. Peak sales on Friday."
    ).assertIsDisplayed()
}
```

- [ ] **Step 3: Write three failing adaptation, keyboard, and font-scale tests**

Add `reports_layout_adapts_from_stack_to_two_panes`. Use one mutable `PosLayoutMode` composition to assert compact first, then expanded:

```kotlin
composeRule.onNodeWithTag("reports-compact").assertIsDisplayed()
composeRule.runOnIdle { layoutMode.value = PosLayoutMode.EXPANDED }
composeRule.onNodeWithTag("reports-expanded").assertIsDisplayed()
composeRule.onNodeWithTag("reports-expanded-primary-pane").assertIsDisplayed()
composeRule.onNodeWithTag("reports-expanded-top-products-pane").assertIsDisplayed()
composeRule.onNodeWithTag("reports-compact").assertDoesNotExist()
```

Add `report_periods_follow_external_keyboard_order` with one fresh composition:

```kotlin
composeRule.onNodeWithTag("reports-period-today").requestFocus().assertIsFocused()
composeRule.onNodeWithTag("reports-period-today").performKeyInput { pressKey(Key.Tab) }
composeRule.onNodeWithTag("reports-period-week").assertIsFocused()
composeRule.onNodeWithTag("reports-period-week").performKeyInput { pressKey(Key.Tab) }
composeRule.onNodeWithTag("reports-period-month").assertIsFocused()
```

Add `reports_remain_scrollable_at_font_scale_1_5` with one fresh composition. Use `CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f))` inside `Box(Modifier.width(400.dp).height(600.dp))`; call `performScrollTo()` on `reports-top-product-coffee` and assert it is displayed. Never call `setContent` twice in one test.

- [ ] **Step 4: Run instrumentation compilation and verify RED**

```bash
./gradlew assembleDebugAndroidTest
```

Expected: Kotlin compilation fails because `ReportsScreen` does not exist. Fix only test harness errors until missing `ReportsScreen` is the reason.

- [ ] **Step 5: Implement Reports screen shell and unavailable state**

Create this public signature:

```kotlin
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onPeriodSelected: (ReportPeriod) -> Unit = {},
)
```

For `ReportsUiState.Unavailable`, render a padded full-size `Column` with heading `Reports`, status `Reports unavailable`, and explanation `Complete report data is not available from the current server contract.` Apply `stateDescription = "Unavailable"`. Do not render chips, values, chart, summary, top products, Retry, or `Demo data`.

For `ReportsUiState.Content`, delegate to one scrollable `LazyColumn`. Put `reports-compact` or `reports-expanded` on the root according to `layoutMode`; preserve this reading order: header, period chips, KPI grid, chart/summary, breakdown, top products. In expanded mode, render chart plus breakdown in the first weighted pane and top products in the second weighted pane, tagged `reports-expanded-primary-pane` and `reports-expanded-top-products-pane`. Keep compact sections stacked.

- [ ] **Step 6: Implement period, KPI, breakdown, and top-product sections**

Use `FilterChip` for `ReportPeriod.entries` with:

```kotlin
Modifier
    .heightIn(min = PosDimensions.touchTarget)
    .testTag("reports-period-${period.name.lowercase()}")
```

Use model IDs as lazy-item/test-tag keys, never display labels. Render metrics in 2 columns for compact and up to 4 columns for expanded, using the existing manual `chunked(columns)` Row pattern. Render breakdown rows and top-product rows as full-width `ElevatedCard`s. Tag every top-product card `reports-top-product-${product.id}`.

Header renders `Reports` with heading semantics and renders `Demo data` only when `content.demoData` is true.

- [ ] **Step 7: Implement accessible compact Canvas chart**

Render visible bar labels and values around a Canvas. The Canvas modifier must include:

```kotlin
Modifier
    .fillMaxWidth()
    .height(180.dp)
    .testTag("reports-chart")
    .semantics {
        contentDescription = "Sales trend chart. ${content.chartSummary}"
    }
```

Draw each bar using only `bar.safeFraction` and available Canvas dimensions. Use `MaterialTheme.colorScheme.primary` converted outside `Canvas` to a captured color. Draw no text on Canvas. Immediately below it, render `content.chartSummary` as visible body text; category/value labels remain normal `Text` nodes. Color must not carry unique information.

- [ ] **Step 8: Verify focused Reports tests GREEN**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.ReportsMoreStateTest" assembleDebugAndroidTest
```

Expected: unit and instrumentation compilation pass. Run API 36 focused class once after the APK exists:

```bash
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./tools/run-device-tests.sh api36
```

Expected: complete instrumentation suite ends with `OK (32 tests)`: prior 27 plus five Reports tests. If another test fails, diagnose root cause before changing production behavior.

---

### Task 3: Build More, Persist Theme, and Replace Shell Placeholders

**Files:**
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/MoreScreen.kt`
- Modify: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ReportsMoreScreenTest.kt`
- Modify: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt:56-95`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt:3-19`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt:20-110`

**Interfaces:**
- Consumes: Task 1 settings contracts, existing `PosTheme`, and Task 2 `ReportsScreen`.
- Produces: `MoreScreen`, persisted root `ThemeSelection`, and release Reports/More routes replacing `PlaceholderScreen`.

- [ ] **Step 1: Write failing More composition test**

Add a controlled `MoreScreen` test to `ReportsMoreScreenTest.kt`:

```kotlin
@Test
fun more_shows_honest_groups_and_emits_theme_selections() {
    val mode = mutableStateOf(PosThemeMode.SYSTEM)
    val accent = mutableStateOf(PosAccent.BLUE)
    composeRule.setContent {
        PosTheme {
            MoreScreen(
                state = MoreUiState(
                    outletLabel = null,
                    userSessionLabel = null,
                    themeMode = mode.value,
                    accent = accent.value,
                    demoData = false,
                ),
                layoutMode = PosLayoutMode.COMPACT,
                onThemeModeSelected = { mode.value = it },
                onAccentSelected = { accent.value = it },
            )
        }
    }

    listOf("Outlet", "User and session", "Appearance", "Printer", "Synchronization")
        .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    composeRule.onAllNodesWithText("Unavailable").assertCountEquals(2)
    composeRule.onNodeWithTag("more-theme-system").assertIsSelected()
    composeRule.onNodeWithTag("more-accent-blue").assertIsSelected()
    composeRule.onNodeWithTag("more-theme-dark")
        .assertHasClickAction()
        .assertHeightIsAtLeast(48.dp)
        .performClick()
    composeRule.onNodeWithTag("more-accent-teal")
        .assertHasClickAction()
        .assertHeightIsAtLeast(48.dp)
        .performClick()
    composeRule.onNodeWithTag("more-theme-dark").assertIsSelected()
    composeRule.onNodeWithTag("more-accent-teal").assertIsSelected()
    composeRule.onNodeWithText("Demo data").assertDoesNotExist()
}
```

- [ ] **Step 2: Write four failing unsupported, adaptation, keyboard, and font-scale tests**

Add `unsupported_more_capabilities_are_disabled_without_actions`. Assert both `Not supported` nodes exist. Tags `more-printer` and `more-synchronization` must satisfy disabled semantics and omit `SemanticsActions.OnClick`:

```kotlin
composeRule.onNodeWithTag("more-printer")
    .assertIsNotEnabled()
    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
composeRule.onNodeWithTag("more-synchronization")
    .assertIsNotEnabled()
    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
```

Add `more_layout_adapts_from_stack_to_two_columns` using one mutable layout-mode composition; assert `more-compact`, update mode, then assert `more-expanded` and absence of `more-compact`.

Add `appearance_controls_follow_external_keyboard_order` in one fresh composition:

```kotlin
composeRule.onNodeWithTag("more-theme-system").requestFocus().assertIsFocused()
composeRule.onNodeWithTag("more-theme-system").performKeyInput { pressKey(Key.Tab) }
composeRule.onNodeWithTag("more-theme-light").assertIsFocused()
composeRule.onNodeWithTag("more-theme-light").performKeyInput { pressKey(Key.Tab) }
composeRule.onNodeWithTag("more-theme-dark").assertIsFocused()
```

This proves source order begins correctly; accent controls follow theme controls in the same composition.

Add `more_remains_scrollable_at_font_scale_1_5` in one fresh composition. At font scale 1.5 in a 400 x 600 dp box, call `performScrollTo()` on `more-synchronization` and assert it is displayed. Never call `setContent` twice in one test.

- [ ] **Step 3: Replace shell placeholder expectations with failing feature assertions**

Add to `ComposeShellTest.kt`:

```kotlin
@Test
fun reports_and_more_release_destinations_are_honest_feature_surfaces() {
    composeRule.onNodeWithTag("root-reports").performClick()
    composeRule.onNodeWithText("Reports unavailable").assertIsDisplayed()
    composeRule.onNodeWithText("Demo data").assertDoesNotExist()

    composeRule.onNodeWithTag("root-more").performClick()
    composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    composeRule.onAllNodesWithText("Unavailable").assertCountEquals(2)
    composeRule.onAllNodesWithText("Not supported").assertCountEquals(2)
}
```

Add persistence-through-recreation coverage. Clear production preferences, recreate once to load defaults, select Teal, recreate, and restore Blue before exit:

```kotlin
@Test
fun theme_selection_persists_across_activity_recreation() {
    composeRule.activityRule.scenario.onActivity { activity ->
        activity.getSharedPreferences("pos_ui_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
    composeRule.activityRule.scenario.recreate()
    composeRule.onNodeWithTag("root-more").performClick()
    composeRule.onNodeWithTag("more-accent-teal").performScrollTo().performClick()

    composeRule.activityRule.scenario.recreate()

    composeRule.onNodeWithTag("root-more").performClick()
    composeRule.onNodeWithTag("more-accent-teal").performScrollTo().assertIsSelected()
    composeRule.onNodeWithTag("more-accent-blue").performClick()
}
```

Wrap the body after acquiring preferences in `try/finally`; in `finally`, call `preferences.edit().clear().commit()`. This keeps the production preference file clean even when an assertion fails.

- [ ] **Step 4: Run instrumentation compilation and verify RED**

```bash
./gradlew assembleDebugAndroidTest
```

Expected: Kotlin compilation fails because `MoreScreen` and expanded `PosShell` contracts do not exist. Existing `ThemePreferencesInstrumentedTest` remains green; do not weaken it.

- [ ] **Step 5: Implement stateless More screen**

Create exact public signature:

```kotlin
@Composable
fun MoreScreen(
    state: MoreUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
)
```

Render one scrollable root tagged `more-compact` or `more-expanded`. Compact stacks five `ElevatedCard` groups. Expanded uses two columns but preserves source/semantic order: Outlet, User and session, Appearance, Printer, Synchronization.

- Outlet value: `state.outletLabel ?: "Unavailable"`.
- User/session value: `state.userSessionLabel ?: "Unavailable"`.
- Appearance: `FilterChip`s for `PosThemeMode.entries`, tagged `more-theme-${mode.name.lowercase()}`; chips for `PosAccent.entries`, tagged `more-accent-${accent.name.lowercase()}`. Every chip uses `heightIn(min = PosDimensions.touchTarget)`.
- Printer: a non-clickable row tagged `more-printer`, with `disabled()` semantics and value `Not supported`.
- Synchronization: same pattern tagged `more-synchronization`.
- Header: `More` with heading semantics; show `Demo data` only when `state.demoData`.

Do not pass callbacks into unsupported rows. Do not add switches for capabilities that cannot work.

- [ ] **Step 6: Move root theme ownership into MainActivity**

Replace fixed `PosTheme { PosShell() }` with controlled state:

```kotlin
setContent {
    val preferences = remember { ThemePreferences.from(applicationContext) }
    var selection by remember { mutableStateOf(preferences.read()) }
    val darkTheme = when (selection.mode) {
        PosThemeMode.SYSTEM -> isSystemInDarkTheme()
        PosThemeMode.LIGHT -> false
        PosThemeMode.DARK -> true
    }

    PosTheme(darkTheme = darkTheme, accent = selection.accent) {
        PosShell(
            themeMode = selection.mode,
            accent = selection.accent,
            onThemeModeSelected = { mode ->
                selection = selection.copy(mode = mode)
                preferences.writeMode(mode)
            },
            onAccentSelected = { accent ->
                selection = selection.copy(accent = accent)
                preferences.writeAccent(accent)
            },
        )
    }
}
```

Use imports for `isSystemInDarkTheme`, `remember`, `mutableStateOf`, delegated `getValue`/`setValue`, settings contracts, and `PosAccent`. Do not introduce coroutine, lifecycle, or ViewModel state.

- [ ] **Step 7: Replace Reports and More placeholder routes**

Expand `PosShell` signature while retaining defaults for direct tests/previews:

```kotlin
@Composable
fun PosShell(
    themeMode: PosThemeMode = PosThemeMode.SYSTEM,
    accent: PosAccent = PosAccent.BLUE,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

Replace the `listOf(REPORTS, MORE)` placeholder loop with explicit routes:

```kotlin
composable(PosDestination.REPORTS.route) {
    ReportsScreen(
        state = ReportsUiState.Unavailable,
        layoutMode = layoutMode,
        modifier = Modifier.testTag("destination-content-reports"),
    )
}
composable(PosDestination.MORE.route) {
    MoreScreen(
        state = MoreUiState(
            outletLabel = null,
            userSessionLabel = null,
            themeMode = themeMode,
            accent = accent,
            demoData = false,
        ),
        layoutMode = layoutMode,
        modifier = Modifier.testTag("destination-content-more"),
        onThemeModeSelected = onThemeModeSelected,
        onAccentSelected = onAccentSelected,
    )
}
```

Delete the now-unused `PlaceholderScreen` import. Keep `PlaceholderScreen.kt` itself because removing unrelated legacy code is outside Task 2E.

- [ ] **Step 8: Verify focused More, persistence, and shell tests GREEN**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.ReportsMoreStateTest" assembleDebugAndroidTest
./tools/run-device-tests.sh api36
```

Expected: unit tests pass and complete API 36 instrumentation suite ends with `OK (39 tests)`: prior 32 plus five More tests and two shell tests. Record exact count from `instrumentation.txt`.

---

### Task 4: Add Debug Previews and Run Required Gates

**Files:**
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/ReportsPreviews.kt`
- Create: `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/MorePreviews.kt`
- Verify: `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReleaseFixtureExclusionTest.kt`
- Generate: `app/build/reports/mobile-pos-task2e/previews/*`
- Generate: `app/build/reports/mobile-pos-devices/api23/*`
- Generate: `app/build/reports/mobile-pos-devices/api36/*`

**Interfaces:**
- Consumes: completed Reports/More surfaces and debug-only immutable fixtures.
- Produces: previews `ReportsCompactPreview`, `ReportsExpandedPreview`, `ReportsFontScalePreview`, `MoreCompactPreview`, `MoreExpandedPreview`, and `MoreFontScalePreview`, plus build/device/accessibility evidence.

- [ ] **Step 1: Add Reports debug fixtures and three previews**

Create one private `ReportsUiState.Content` fixture using the same caller-provided labels exercised in tests and `demoData = true`. Expose:

```kotlin
@Preview(name = "Reports compact light Blue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun ReportsCompactPreview() {
    PosTheme(darkTheme = false, accent = PosAccent.BLUE) {
        ReportsScreen(reportsDemoState, PosLayoutMode.COMPACT)
    }
}

@Preview(name = "Reports expanded dark Teal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun ReportsExpandedPreview() {
    PosTheme(darkTheme = true, accent = PosAccent.TEAL) {
        ReportsScreen(reportsDemoState, PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "Reports font 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun ReportsFontScalePreview() {
    PosTheme(darkTheme = false, accent = PosAccent.BLUE) {
        ReportsScreen(reportsDemoState, PosLayoutMode.EXPANDED)
    }
}
```

- [ ] **Step 2: Add More debug fixtures and three previews**

Create one private `MoreUiState` fixture with outlet `Outlet Menteng`, user/session `Ayu · Open session`, `demoData = true`, and the selection passed by each preview. Expose:

```kotlin
@Preview(name = "More compact dark Blue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MoreCompactPreview() {
    PosTheme(darkTheme = true, accent = PosAccent.BLUE) {
        MoreScreen(moreDemoState(PosThemeMode.DARK, PosAccent.BLUE), PosLayoutMode.COMPACT)
    }
}

@Preview(name = "More expanded light Teal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun MoreExpandedPreview() {
    PosTheme(darkTheme = false, accent = PosAccent.TEAL) {
        MoreScreen(moreDemoState(PosThemeMode.LIGHT, PosAccent.TEAL), PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "More font 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun MoreFontScalePreview() {
    PosTheme(darkTheme = false, accent = PosAccent.TEAL) {
        MoreScreen(moreDemoState(PosThemeMode.SYSTEM, PosAccent.TEAL), PosLayoutMode.EXPANDED)
    }
}
```

No fixture moves to `main`. Do not read `SharedPreferences` in previews.

- [ ] **Step 3: Verify release-fixture exclusion and focused build**

```bash
./gradlew testDebugUnitTest --tests "com.rotiropi.pos_erpnext.ui.ReportsMoreStateTest" --tests "com.rotiropi.pos_erpnext.ui.ReleaseFixtureExclusionTest" assembleDebug assembleDebugAndroidTest
```

Expected: PASS. Existing generic preview-path test must discover the new files under `app/src/debug`; modify it only if a demonstrated path bug exists.

- [ ] **Step 4: Run full available Gradle gate**

```bash
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest compileReleaseKotlin
```

Expected: exit 0. Record unit-test count and lint warning count. Do not claim `testReleaseUnitTest`; this project does not expose that task unless Gradle proves otherwise.

- [ ] **Step 5: Render all six previews with semantics**

First select the Android Studio instance that has this exact worktree open:

```bash
android studio check
mkdir -p app/build/reports/mobile-pos-task2e/previews
```

For each preview, run the same pattern with this worktree path supplied to `--project`; redirect semantics to the paired text file:

```bash
android studio render-compose-preview \
  --project "$PWD" \
  --print-semantics \
  --output-image-file "app/build/reports/mobile-pos-task2e/previews/reports-compact.png" \
  "app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/ReportsPreviews.kt" \
  "ReportsCompactPreview" \
  > "app/build/reports/mobile-pos-task2e/previews/reports-compact.semantics.txt"
```

Repeat exactly for:

| Kotlin file | Composable | Output stem |
|---|---|---|
| `ReportsPreviews.kt` | `ReportsExpandedPreview` | `reports-expanded` |
| `ReportsPreviews.kt` | `ReportsFontScalePreview` | `reports-font-scale` |
| `MorePreviews.kt` | `MoreCompactPreview` | `more-compact` |
| `MorePreviews.kt` | `MoreExpandedPreview` | `more-expanded` |
| `MorePreviews.kt` | `MoreFontScalePreview` | `more-font-scale` |

Require six non-empty PNG files and six non-empty semantics files. Inspect visible `Demo data`, all required groups/sections, chart summary, selected controls, `Not supported`, light/dark, Blue/Teal, compact/expanded, portrait/landscape, and font scale 1.5. A render failure is a failed gate, not a skipped success.

- [ ] **Step 6: Run API 23 device suite**

```bash
./tools/run-device-tests.sh api23
```

Expected: exit 0; `app/build/reports/mobile-pos-devices/api23/instrumentation.txt` ends with `OK (39 tests)`.

- [ ] **Step 7: Run API 36 device suite**

```bash
./tools/run-device-tests.sh api36
```

Expected: exit 0; `app/build/reports/mobile-pos-devices/api36/instrumentation.txt` ends with `OK (39 tests)`.

- [ ] **Step 8: Review accessibility evidence and diff**

Check rendered semantics and tests for:

- 48 dp period/theme/accent controls.
- Selected period/theme/accent states.
- Disabled printer/synchronization states without click actions.
- Visible chart summary and chart content description.
- Heading and reading order.
- Scrollability at font scale 1.5.
- Honest release Reports, outlet, and user/session states.

Run:

```bash
git diff --check
git status --short
git diff --stat
git diff -- app/src/main app/src/debug app/src/test app/src/androidTest docs/superpowers
```

Require only Task 2E production/test/preview files plus approved spec/plan. Require no Gradle dependency, API, DTO, repository, ViewModel, authentication, bootstrap, printer, synchronization, camera, or Task 3 changes. Report every warning, skipped gate, and failed gate accurately. Stop without commit, push, branch switch, or Task 3 work.
