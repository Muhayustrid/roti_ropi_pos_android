# Task 2E Reports and More Visual Surfaces Design

## Goal

Add honest Compose surfaces for Reports and More, including application-private theme mode and accent persistence. Preserve later authentication, bootstrap, report-contract, printer, and synchronization boundaries.

## Scope

- Reports period selection, KPI slots, semantic breakdown rows, a compact Canvas chart, a visible textual chart summary, and top-product slots.
- More groups for outlet, user/session, appearance, printer, and synchronization.
- Theme modes `System`, `Light`, and `Dark`.
- Existing `PosAccent.BLUE` and `PosAccent.TEAL` choices.
- Application-private `SharedPreferences` persistence for theme mode and accent.
- Adaptive compact and expanded compositions.
- Debug-only populated fixtures marked `Demo data`.
- Representative previews covering light, dark, both accents, phone, tablet, portrait, landscape, and font scale 1.5.

## Boundaries

- Release Reports uses an honest `Reports unavailable` state because the current 14-endpoint contract has no complete report aggregates.
- The app must not derive report aggregates by summing bounded or paginated API results.
- Release More shows outlet and user/session as unavailable until Task 4 supplies authoritative bootstrap data.
- Printer and synchronization remain disabled with `Not supported`; they do not emit actions.
- Synthetic report, outlet, and user records exist only in debug previews and tests and display `Demo data`.
- Report labels remain caller-provided strings. Task 2E performs no currency parsing, accounting, or business aggregation.
- Canvas arithmetic is limited to drawing geometry from caller-provided normalized fractions.
- No endpoint, DTO, repository, ViewModel, authentication, bootstrap, mutation, recovery, printer, synchronization, camera, or dependency work.
- Stop before Task 3.

## Architecture

Use feature-local immutable visual models and controlled composables:

- `ReportsUiState` distinguishes `Unavailable` from populated `Content`.
- `ReportsContent` owns selected period, KPI labels, breakdown rows, normalized chart bars, visible chart summary, top-product labels, and the demo marker.
- `ReportsScreen` renders state, selects compact or expanded composition from `PosLayoutMode`, and emits only period-selection callbacks.
- `MoreUiState` owns optional outlet/user labels plus selected theme mode and accent. Missing outlet or user values render `Unavailable`.
- `MoreScreen` renders grouped settings and emits only theme-mode and accent callbacks.
- `ThemePreferences` is a small wrapper over application-private `SharedPreferences`. It reads safe defaults and writes enum names.
- `MainActivity` owns remembered theme state, resolves `System` against `isSystemInDarkTheme()`, applies `PosTheme`, updates state immediately after a selection, and persists the selection.
- `PosShell` receives selected theme values and callbacks, replaces only the Reports and More placeholders, and supplies `ReportsUiState.Unavailable` plus honest release More data.
- Debug preview fixtures supply populated states without entering release runtime.

Do not introduce a ViewModel or state-management abstraction for two local preferences. Future authentication/bootstrap work can replace nullable outlet/user labels without changing the screen contract.

## Reports Composition

### State contract

`ReportsUiState.Unavailable` renders:

- `Reports` heading.
- `Reports unavailable` status.
- A short explanation that complete report data is not available from the current server contract.
- No KPI values, chart, period actions, top products, retry action, or demo marker.

`ReportsUiState.Content` renders caller-provided content and requires `isDemoData = true` for all Task 2E populated fixtures. Production navigation does not construct this state.

Each chart bar contains:

- A stable ID.
- A visible category label.
- A visible value label supplied by the caller.
- A normalized height fraction. Rendering clamps this fraction to `0f..1f` only for safe geometry.

### Layout

Compact layout uses one vertically scrollable column in this order:

1. Heading and optional `Demo data` marker.
2. Period selection chips.
3. KPI card grid.
4. Chart and visible text summary.
5. Semantic breakdown rows.
6. Top-product rows.

Expanded layout keeps the same reading order while using available width:

- KPI cards span a wider grid.
- Chart and semantic breakdown occupy one pane.
- Top products occupy the adjacent pane.

The Canvas is compact and decorative beyond its chart semantics. A visible text summary communicates the same trend without relying on shape or color. Every period chip exposes selected semantics and a minimum 48 dp target.

## More Composition

`MoreScreen` always uses a vertically scrollable surface. Compact mode stacks groups; expanded mode may place groups in two columns while preserving semantic and keyboard order.

Groups render as follows:

1. **Outlet** — authoritative label when supplied by a future caller; otherwise `Unavailable`.
2. **User and session** — authoritative label when supplied by a future caller; otherwise `Unavailable`.
3. **Appearance** — selectable `System`, `Light`, and `Dark` modes plus `Blue` and `Teal` accents.
4. **Printer** — visible disabled row with `Not supported`.
5. **Synchronization** — visible disabled row with `Not supported`.

Theme mode and accent controls expose selected semantics, minimum 48 dp targets, and logical keyboard order. Disabled capability rows expose disabled semantics and have no click callbacks.

## Theme Persistence and Data Flow

Use one application-private preference file. Store theme mode and accent as enum names under stable keys. Read behavior:

- Missing or unknown theme mode becomes `System`.
- Missing or unknown accent becomes `PosAccent.BLUE`.
- Preference corruption never crashes composition.

At app startup:

1. `MainActivity` reads both values through `ThemePreferences`.
2. Root Compose state remembers those values.
3. `System` resolves with `isSystemInDarkTheme()`; explicit modes resolve directly.
4. `PosTheme` receives the resolved dark flag and selected accent.
5. `PosShell` receives the selected values and callbacks for More.

On selection, the callback updates root Compose state first and writes the enum name with `SharedPreferences.edit().putString(...).apply()`. Recomposition updates the whole app immediately. Restart and activity recreation read the persisted values without a repository or ViewModel.

Reports data flow remains visual-only: `PosShell` passes `Unavailable` in release; previews/tests pass immutable demo content; period selection emits a callback but performs no load.

## Error and Unsupported States

- Reports unavailable is a capability state, not a transient failure. It has no misleading Retry action.
- More outlet/user unavailable rows do not imply that login or bootstrap has run.
- Unknown preference values fall back safely and are replaced only after the user makes a selection.
- Printer and synchronization stay visible but disabled so capability status is explicit.
- Task 2E adds no network error, offline, loading, or recovery state because no runtime request exists.

## Accessibility and Adaptation

- Minimum 48 dp target for every enabled selection control.
- Heading semantics for screen and group titles.
- Selected semantics for report period, theme mode, and accent.
- Disabled semantics for printer and synchronization rows.
- Visible chart labels and a textual chart summary; color is not the sole information channel.
- Logical TalkBack and external-keyboard order follows visual reading order.
- Compact and expanded surfaces remain scrollable at font scale 1.5 without hiding primary settings.
- Previews and device tests cover phone/tablet and portrait/landscape layouts.

## Testing

Follow TDD and verify each RED failure before production code.

### Unit tests

- Unknown or missing stored theme mode resolves to `System`.
- Unknown or missing stored accent resolves to `PosAccent.BLUE`.
- Valid theme mode and accent values round-trip through preference parsing.
- Reports models retain caller-provided KPI, breakdown, chart-summary, and top-product labels without business calculations.
- Chart rendering input remains bounded to safe normalized geometry.

### Compose and device tests

- Release Reports shows `Reports unavailable` and has no demo marker, KPI content, chart, period action, or retry action.
- Populated Reports shows `Demo data`, period selected semantics, caller-provided labels, Canvas chart semantics, and visible textual summary.
- Compact and expanded Reports preserve expected section order and scrollability.
- More shows all five groups, honest outlet/user unavailable values, selected theme/accent semantics, and callback behavior.
- Theme and accent controls meet 48 dp targets and remain reachable by keyboard.
- Printer and synchronization rows are disabled, say `Not supported`, and expose no click action.
- Font scale 1.5 remains scrollable without clipping primary settings.
- Real application-private `SharedPreferences` preserves valid selections across recreation/reload and safely handles invalid stored values.
- Shell navigation renders Reports and More feature surfaces instead of `PlaceholderScreen`.
- Existing release-fixture exclusion continues to prove populated fixtures remain outside `main` source.

## Files

Expected additions:

- `app/src/main/java/com/rotiropi/pos_erpnext/ui/reports/ReportsUiState.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/reports/ReportsScreen.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/MoreUiState.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/MoreScreen.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/ThemePreferences.kt`
- `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/ReportsPreviews.kt`
- `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/MorePreviews.kt`
- Focused unit and instrumentation tests for Reports, More, and theme preferences.

Expected modifications:

- `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReleaseFixtureExclusionTest.kt` only if its current generic coverage does not include new preview files.

No Gradle dependency change is expected. Compose Material 3, Canvas, Navigation Compose, Android `SharedPreferences`, JUnit 4, and existing Compose test dependencies are sufficient.

## Preview and Completion Gate

Use a representative preview set that covers every required axis at least once rather than generating a full Cartesian matrix:

- Reports compact light/Blue portrait.
- Reports expanded dark/Teal landscape.
- Reports font scale 1.5.
- More compact dark/Blue portrait.
- More expanded light/Teal landscape.
- More font scale 1.5.

Task 2E is complete only when:

1. Focused unit and Compose tests pass.
2. `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` pass.
3. API 23 and API 36 device suites pass through `tools/run-device-tests.sh`.
4. Preview images and semantics are inspected for light/dark, both accents, phone/tablet, portrait/landscape, and font scale 1.5.
5. Accessibility semantics, 48 dp targets, keyboard order, disabled unsupported rows, and chart text alternatives are verified.
6. `git diff --check` passes and diff review finds only Task 2E files.
7. No release demo fixture, report aggregation, backend integration, or unsupported capability claim exists.
8. Work stops before Task 3 and reports `feat: add Reports and More UI`.
