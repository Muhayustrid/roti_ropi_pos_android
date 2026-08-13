# Three-Tab Navigation and Indonesian-First Localization — Design

**Date:** 2026-08-12
**Supersedes:** the five-destination shell decisions recorded for Task 2B in
`docs/mobile-pos/implementation-plan.md`, and the "strings stay English"
and "navigation stays five tabs" constraints from the earlier design-refresh brief.

## Why

The approved design source is the standalone Compose prototype at
`/Users/rotiropi/DockerERPNext/pos-ui-prototype` (package
`com.rotiropi.pos.prototype`, read-only, not part of this repository). Its
navigation is three top-level destinations, and its interface language is
Indonesian. The app shipped five destinations and hard-coded English, so the two
diverged in the two places a cashier sees most: the bottom bar and every label.

The prototype is authoritative for design. Where the app has capability the
prototype has no counterpart for, the capability is kept and re-homed rather than
matched away.

## Navigation

Three top-level destinations, mirroring the prototype's `TopLevel` enum:

| Route | Label (id) | Label (en) | Was |
| --- | --- | --- | --- |
| `cashier` | Kasir | Cashier | tab, elevated center action |
| `history` | Riwayat | History | child route behind a button on Reports |
| `more` | Lainnya | More | tab |

- The elevated center Cashier action is removed. The prototype's bottom bar is
  three equal `NavigationBarItem`s with no raised action, so the raised circle and
  its `cashier-elevated-action` tag go with it.
- `history` is promoted from a child route to a top-level destination. The
  `open-history` button on Reports, which was the only route into sale history and
  had no test covering it, is removed with it.
- The start destination becomes `cashier`. Post-opening routing that previously
  chose between `HOME` and `CASHIER` now always resolves to `cashier`.

## Products and Reports

Kept, and reached from Lainnya as child routes `products` and `reports`. Both
screens, their state contracts, their grid-column functions, and their tests stay
as they are; only their entry point moves. The prototype has no counterpart for
either, which is a reason to re-home them, not to delete working surfaces.

## Dashboard

Deleted. `DashboardScreen`, `DashboardUiState`, `DashboardContent`,
`DashboardMetric`, `DashboardQuickAction`, `RecentTransaction`, `LowStockItem`,
`dashboardGridColumns`, the dashboard debug fixtures and previews, the dashboard
tests, and `ic_home.xml` all go. It aggregated figures ERPNext already owns and
never had a live data source: every release path rendered
`DashboardUiState.Unavailable`. Nothing else consumes it.

## Language

Indonesian is the default and the fallback; English is a selectable alternative.

- `values/strings.xml` holds Indonesian, so any locale without a better match
  resolves to Indonesian.
- `values-en/strings.xml` holds English.
- A stored `PosLanguage` preference (`INDONESIAN`, `ENGLISH`, default
  `INDONESIAN`) is applied through `AppCompatDelegate.setApplicationLocales`, so
  the app opens in Indonesian even on an English-locale device. It is selectable
  from Lainnya beside the existing theme and accent controls.
- `minSdk 23` requires the AndroidX compatibility path, which resolves app locales
  through an `AppCompatActivity` context. `MainActivity` therefore becomes an
  `AppCompatActivity` — its theme is already `Theme.Material3.DayNight.NoActionBar`,
  so this is a base-class change, not a restyle — and the manifest gains
  `AppLocalesMetadataHolderService` with `autoStoreLocales` for API 32 and below.
- `appcompat` becomes an explicit dependency. It is already on the classpath
  transitively through `com.google.android.material`, and depending on a transitive
  for an API we call directly is fragile.

## Hard-coded strings

Every user-facing literal moves to a string resource. Three categories stay
literal, because a resource would be wrong rather than merely unnecessary:

- Text the server owns: currency codes, amounts, product names, mode-of-payment
  names, warehouse and price-list names, and server validation messages. ERPNext is
  authoritative for these and they are already localized, or deliberately not, at
  the source.
- `testTag` values. They are test identifiers, not text, and they stay in English
  and unchanged so the existing contracts keep holding.
- Route names and enum route values, which are identifiers.

Test assertions that matched a literal now resolve the same string from resources.
This keeps each assertion exactly as strong as it was — it still asserts the
specific string that specific surface must show — while making it locale-independent.
That is a different act from relaxing an assertion to make a failure disappear,
which remains disallowed.

## Dark mode

Kept. The prototype defines only `lightColorScheme` and its `PosColors` has no
dark variant, so the light palette is taken from the prototype directly and the
dark palette is **derived here, not ported**: the same hues carried to
Material 3 dark tonal positions. Any dark value in this app is therefore ours, and
a future prototype update cannot be diffed against it.

## Currency

The prototype's `formatRupiah` hard-codes `"Rp "`. It is not ported. This app
takes the currency from each server response and honors
`PaymentAmountPolicy(currency, decimalPlaces, minimum, apiSyntax, rounding,
policyVersion)`, which is why instrumentation asserts `IDR 525,000` rather than
`Rp 525.000`. The prototype's visual treatment of amounts is adopted; its
formatter is not.

## Out of scope

The side rail and the brand bar with a live clock are prototype features this
design does not yet adopt; they are separate work, tracked in the plan.
