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

**Superseded by Task 4 on 2026-08-13.** Phones now run portrait only, so phone
landscape is not a window this app runs in and this baseline no longer applies.
Task 4 records what those failures actually were.

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
- [x] Write failing tests first: a `PosLanguage` parse/default unit test, and one
      instrumentation test asserting a screen renders the Indonesian string when
      the app locale is Indonesian.
- [x] Add `appcompat` explicitly; switch `MainActivity` to `AppCompatActivity`;
      register `AppLocalesMetadataHolderService` with `autoStoreLocales`.
- [x] Move Indonesian into `values/strings.xml` and English into
      `values-en/strings.xml`. Name keys by surface and role
      (`cashier_search_label`, not `text1`).
- [x] Replace literals screen by screen, smallest screen first, running
      `:app:testDebugUnitTest` and the screen's instrumentation class after each.
      Leave server-owned text alone. Strings a ViewModel supplies to a screen
      resolve through a resource lookup at the UI edge, not inside the ViewModel.
- [x] Add the language selector to Lainnya beside theme and accent.

**Verification:** unit + lint + assemble; instrumentation for each touched screen
at phone portrait; one run with the device locale set to English confirming the
app still opens in Indonesian.

**Verified 2026-08-13** on `emulator-5554` (API 36):
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease
:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` BUILD
SUCCESSFUL. `values/strings.xml` and `values-en/strings.xml` each hold 312 keys
with no key present in only one file.

Instrumentation for package `com.rotiropi.pos_erpnext.ui`, excluding
`@SpecialHarnessOnly`, is 110 tests — 107 from Task 1 plus `AppLanguageTest` (2)
and `ReportsMoreScreenTest.language_chips_…` (1):

| Window | Result |
| --- | --- |
| Phone portrait 1080x1920 @420 | 0 failed on the final run; 1 failed on two earlier runs |
| Tablet 1600x2560 @320 | 0 failed |
| Phone landscape 2400x1080 @420 | 9 failed |

The device locale stayed `en-US` for every run, so the whole suite doubles as the
"opens in Indonesian on an English device" check: `AppLanguageTest` asserts the
Sign-In screen renders the `values/` string and that the English one is absent.

Both failure sets are pre-existing, confirmed by stashing this work and rebuilding:

- Portrait: `CustomerSearchRootTest.productionRootFirstOpenRunsOneDebouncedBlankSearchAndReopenIsNoOp`
  only, and it is the known flake recorded under Task 1, not a regression. It
  failed twice with this work applied, then passed on the final run; on the
  stashed baseline it failed 3/3 repeat runs at this same window size. Its
  `waitUntil(2_000)` races its own 800 ms fixture delay.
- Landscape: the same 9 names fail on the stashed baseline, in the same tests.
  This is a strict subset of the documented 17-test landscape baseline.

Three deliberate mechanism choices, recorded because they are not obvious from the
diff:

- `ReceiptContent.status` became `@StringRes Int`. `SaleStatus` is a closed enum,
  so no server string can reach the screen through it, and the label now follows
  the selected language instead of the one in force when the sale was mapped.
- `ReceiptContent.items` became `ReceiptItemLine(summary, batches, serials)`.
  Pre-joining the line would have frozen this app's `Batch:` and `Serial:`
  prefixes into the mapper; keeping the parts separate leaves the server numbers
  verbatim and resolves only the prefixes at the UI edge.
- `CashierProduct.priceList` became `UiText`. The catalog endpoint names no price
  list, so the Cashier supplies its own stand-in label, which must translate;
  responses that do carry a name still pass through as `UiText.Raw`.

---

### Task 3: Update the historical record

**Files:**
- Modify: `docs/mobile-pos/implementation-plan.md`
- Modify: `AGENTS.md`

**Steps:**
- [x] Mark the Task 2B five-destination shell as superseded, pointing at the
      design document. Do not rewrite the 2026-07-30 evidence itself: it records
      what was verified at that time and stays accurate as history.
- [x] State the string-resource rule in `AGENTS.md` so later work inherits it.

---

### Task 4: Lock phones to portrait and settle the landscape failures

The 17-test landscape baseline recorded above was never a layout bug to fix. The
product does not ship a landscape phone: a cashier on a phone works portrait, and
landscape belongs to tablets on a counter. Once orientation follows that, the
window those tests failed in no longer exists on a phone.

**Files:**
- Create: `app/src/main/res/values/bools.xml` (`pos_lock_portrait` true)
- Create: `app/src/main/res/values-sw600dp/bools.xml` (`pos_lock_portrait` false)
- Create: `app/src/main/java/com/rotiropi/pos_erpnext/ui/PosOrientation.kt`
- Create: `app/src/test/java/com/rotiropi/pos_erpnext/ui/PosOrientationTest.kt`
- Modify: `app/src/main/java/com/rotiropi/pos_erpnext/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ClosingScreenTest.kt`
- Modify: `AGENTS.md`

**Interfaces:**
- Produces: `posRequestedOrientation(resources): Int`, returning
  `SCREEN_ORIENTATION_PORTRAIT` below `sw600dp` and `SCREEN_ORIENTATION_UNSPECIFIED`
  at or above it.
- Consumes: the platform's own `sw600dp` qualifier, so Android's resource matching
  draws the phone/tablet line instead of a dp comparison written in Kotlin. This is
  separate from `PosWindow.isTall`, which answers the finer question of whether a
  window can hold two full-height columns.

**Steps:**
- [x] Write `PosOrientationTest` first, covering a phone, a tablet, and a rotated
      phone that must not become a tablet.
- [x] Add the bool in both resource folders and `posRequestedOrientation`, then set
      `requestedOrientation` in `MainActivity.onCreate`.
- [x] Prove the `sw600dp` qualifier is load-bearing by moving `values-sw600dp/`
      aside and watching the tablet case fail.
- [x] Re-run the landscape-failing set and confirm the remaining failures are not
      caused by window height.
- [x] Run the suite on a small window as well as a large one, and fix what it
      finds without weakening an assertion.
- [x] State the orientation rule and the small-window rule in `AGENTS.md`.

**Verification:** unit + lint + assemble; instrumentation on both API levels.

**Verified 2026-08-13**: `./gradlew :app:testDebugUnitTest :app:lintDebug
:app:lintRelease :app:assembleDebug :app:assembleRelease` BUILD SUCCESSFUL, 518
unit tests with 0 failures. Instrumentation for package
`com.rotiropi.pos_erpnext.ui`, excluding `@SpecialHarnessOnly`, is 110 tests:

| Device | Window | Result |
| --- | --- | --- |
| mobile-pos-api25 | 320x640 @160 | OK (110) |
| mobile-pos-api36 | phone portrait 1080x1920 @420 | OK (110) |
| mobile-pos-api36 | tablet 1600x2560 @320 | OK (110) |

Phone landscape is no longer a window this app runs in, so it is not a row in that
table rather than a row with failures in it.

Two findings worth keeping, because neither is visible from the diff:

- The nine landscape failures had two unrelated causes, separated by stashing this
  work, rebuilding, reinstalling, and re-running. Six were window height: a 411dp
  window clips a POS body, so nodes report "not displayed" or a height of 0.0.dp
  for a `LazyColumn` that was never measured. Three were external-keyboard focus
  and had nothing to do with height — they fail at phone portrait too, and they
  fail identically on the stashed baseline. Those three pass inside the full-package
  run and fail when driven alone with `-e class`, on both API levels, which points
  at IME state carried between tests rather than at application code. They are not
  fixed here and are not claimed to be.
- API 25 at 320x640 @160 found a failure that no 1080dp-wide run did:
  `ClosingScreenTest.More_Closing_child_route_loads_preview_and_back_returns_to_More`.
  `MoreScreen` stacks its groups in compact, so `more-closing` sat below the fold
  and `performClick()` reached nothing. It fails identically on the stashed
  baseline, so it is pre-existing rather than a regression; it had simply never
  been exercised, because API 25 had only ever run a focused thread test, not the
  `ui` package. Fixed with `performScrollTo()` before the click, with
  `assertIsDisplayed()`, the load count, and the return-to-More assertion all
  unchanged.

`targetSdk 36` ignores `screenOrientation` and `setRequestedOrientation()` at
`sw600dp` and above. That matches this intent instead of fighting it — tablets are
meant to rotate — so no
`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out is used.

