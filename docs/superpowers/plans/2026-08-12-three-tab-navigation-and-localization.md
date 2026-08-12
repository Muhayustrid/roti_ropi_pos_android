# Three-Tab Navigation and Indonesian-First Localization — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:executing-plans` to implement this plan task-by-task.

**Design:** `docs/superpowers/specs/2026-08-12-three-tab-navigation-and-localization-design.md`

**Goal:** Match the approved prototype's three-destination navigation and
Indonesian-first interface, keep Products and Reports reachable from Lainnya,
remove Dashboard, and move every user-facing literal into string resources.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose,
AndroidX AppCompat locale APIs, minSdk 23, targetSdk 36.

## Global Constraints

- `testTag` values stay exactly as they are, except tags belonging to deleted
  surfaces. Visual treatment changes inside them.
- Assertions are rewritten to resolve strings from resources, never weakened or
  deleted to make a failure disappear. If a step needs an assertion to become
  less strict, stop and report instead.
- Server-owned text stays literal: currency, amounts, item and payment-mode
  names, warehouse and price-list names, server validation messages.
- Do not port the prototype's `formatRupiah`. Keep this app's currency handling
  and `PaymentAmountPolicy`.
- Keep `minSdk 23` working.
- Do not commit or push without explicit user approval.
- Verify each task with `./gradlew :app:testDebugUnitTest :app:lintDebug
  :app:assembleDebug`, plus instrumentation on `mobile-pos-api36` at phone
  portrait and tablet for tasks that touch UI structure.

## Task order and why

Dashboard's deletion and the reduction to three destinations are one task, not
two. `home` rendered nothing but Dashboard, so deleting the screen already forces
the enum, the navigation bar, `PosFoundationTest`, and `ComposeShellTest` to
change; splitting the work would rewrite the same route list and the same
assertions twice for no gain. Localization comes after, once the surviving
surfaces are settled, and rewrites each remaining assertion to resolve its string
from resources exactly once.

## Known pre-existing failures

17 instrumentation tests in `com.rotiropi.pos_erpnext.ui` fail at phone landscape
(2400x1080 @420) on `main` as of commit `c9c361c`, in `CustomerSearchRootTest` (9),
`CustomerSearchSheetTest` (2), `ClosingScreenTest` (2), and one each in
`ComposeShellTest`, `ReportsMoreScreenTest`, `DashboardProductsScreenTest`, and
`CatalogAccessibilityTest`. They are not caused by this work and are not fixed
here. Compare against that baseline, not against zero, when verifying landscape.

---

### Task 1: Delete Dashboard and reduce to three destinations

**Files:**
- Delete: `ui/dashboard/DashboardScreen.kt` (holds `DashboardUiState`,
  `DashboardContent`, `DashboardMetric`, `DashboardQuickAction`,
  `RecentTransaction`, `LowStockItem`, and `dashboardGridColumns` in one file)
- Delete: `app/src/debug/.../preview/DashboardPreviews.kt`
- Delete: `PosDemoStates.dashboard` and its fixtures in both `debug` and
  `release` source sets
- Delete: `app/src/main/res/drawable/ic_home.xml`, `ic_products.xml`, `ic_reports.xml`
- Modify: `ui/navigation/PosDestination.kt` (three constants: `cashier`,
  `history`, `more`)
- Modify: `ui/components/RootNavigationBar.kt` (drop the elevated action and
  `cashier-elevated-action`; three equal items, as the prototype has)
- Modify: `ui/navigation/PosShell.kt` (drop the `home` composable; `history`
  promoted to top level; `products`/`reports` become child routes; `open-history`
  removed; the `selectedDestination` fallback becomes `CASHIER`)
- Modify: `ui/settings/MoreScreen.kt` (entries for Products and Reports, tagged
  `more-products` and `more-reports`)
- Modify: `ui/Task4RootHost.kt` (post-opening routing always resolves to `cashier`)
- Rename+prune: `androidTest/.../DashboardProductsScreenTest.kt` →
  `ProductsScreenTest.kt`, dropping the five dashboard cases and keeping the
  seven Products cases; the mixed
  `dashboard_and_products_errors_are_announced` keeps only its Products half
- Rename+prune: `test/.../DashboardProductsStateTest.kt` → `ProductsStateTest.kt`,
  dropping `dashboard_grid_expands_without_changing_compact_density`
- Modify: `test/.../PosFoundationTest.kt` (route list and centered-index assertion)
- Modify: `androidTest/.../ComposeShellTest.kt` (route lists, tab-order traversal,
  the dashboard assertions, and the whole-screen `Unavailable`/`Not supported`
  counts, which shift when Lainnya gains entries)
- Modify: `androidTest/.../Task4RootOpeningRoutingTest.kt`

**Steps:**
- [x] Update `PosFoundationTest` and `ComposeShellTest` to the intended
      three-tab contract first, and watch them fail.
- [x] Delete every dashboard file and fixture; confirm no dashboard symbol
      survives anywhere.
- [x] Change `PosDestination`, then the navigation bar, then the shell graph.
- [x] Add Products and Reports entries to Lainnya and assert both navigate.
- [x] Re-run the full `com.rotiropi.pos_erpnext.ui` package, excluding
      `@SpecialHarnessOnly`, and diff the failure set against the baseline above.

**Verification:** unit + lint + assemble; `ReleaseFixtureExclusionTest` still
passes; instrumentation at phone portrait and tablet green; phone landscape no
worse than the 17-test baseline.

**Verified 2026-08-12** on `mobile-pos-api36`:
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease
:app:assembleDebug :app:assembleRelease` BUILD SUCCESSFUL. Instrumentation for
package `com.rotiropi.pos_erpnext.ui`, excluding `@SpecialHarnessOnly`, is 107
tests: phone portrait (1080x1920 @420) 0 failed; tablet (1600x2560 @320) 1
failed; phone landscape (2400x1080 @420) 9 failed.

`CustomerSearchRootTest.productionRootFirstOpenRunsOneDebouncedBlankSearchAndReopenIsNoOp`
is flaky, not broken by this work: it fails on `main` with these changes stashed
(`CustomerSearchRootTest.kt:104`, `waitUntil(2_000) { exists("customer-loading") }`
timing out against its own 800 ms fixture delay), and it passes and fails across
repeat runs at the same window size. It is the single tablet failure and one of
the nine at landscape.

The other eight landscape failures are a strict subset of the 17-test baseline.
`DashboardProductsScreenTest` and `ComposeShellTest` left the list because the
dashboard case is deleted and the shell no longer renders five tabs; six
`CustomerSearchRootTest` cases left it because the customer sheet now opens from
a Cashier start destination instead of a Home one. Nothing new appeared.

---

### Task 2: Extract strings and add the language preference

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (becomes Indonesian)
- Create: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/PosLanguage.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/ui/settings/ThemePreferences.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt` (to `AppCompatActivity`)
- Modify: `app/src/main/AndroidManifest.xml` (`AppLocalesMetadataHolderService`)
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml` (explicit `appcompat`)
- Modify: every `ui/` screen holding a user-facing literal
- Modify: the instrumentation tests asserting those literals

**Interfaces:**
- Produces: `PosLanguage { INDONESIAN, ENGLISH }`, stored default `INDONESIAN`,
  applied via `AppCompatDelegate.setApplicationLocales`.
- Consumes: existing `ThemePreferences` storage; language joins mode and accent.

**Steps:**
- [ ] Write failing tests first: a `PosLanguage` parse/default unit test, and one
      instrumentation test asserting a screen renders the Indonesian string when
      the app locale is Indonesian.
- [ ] Add `appcompat` explicitly; switch `MainActivity` to `AppCompatActivity`;
      register `AppLocalesMetadataHolderService` with `autoStoreLocales`.
- [ ] Move Indonesian into `values/strings.xml` and English into
      `values-en/strings.xml`. Name keys by surface and role
      (`cashier_search_label`, not `text1`).
- [ ] Replace literals screen by screen, smallest screen first, running
      `:app:testDebugUnitTest` and the screen's instrumentation class after each.
      Leave server-owned text alone. Strings a ViewModel supplies to a screen
      resolve through a resource lookup at the UI edge, not inside the ViewModel.
- [ ] Add the language selector to Lainnya beside theme and accent.

**Verification:** unit + lint + assemble; instrumentation for each touched screen
at phone portrait; one run with the device locale set to English confirming the
app still opens in Indonesian.

---

### Task 3: Update the historical record

**Files:**
- Modify: `docs/mobile-pos/implementation-plan.md`
- Modify: `AGENTS.md`

**Steps:**
- [ ] Mark the Task 2B five-destination shell as superseded, pointing at the
      design document. Do not rewrite the 2026-07-30 evidence itself: it records
      what was verified at that time and stays accurate as history.
- [ ] State the string-resource rule in `AGENTS.md` so later work inherits it.
