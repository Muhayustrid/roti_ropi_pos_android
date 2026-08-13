# Prototype Adaptive UI Foundation Design

## Goal

Refine `prototype/android-prototype` into a consistent, calm Compose POS UI using
the `#5F7DF7` primary accent. This phase establishes shared visual foundations
without changing navigation routes, session state, cart rules, totals, payment
flow, or any other behavior.

## Scope

This phase modifies only the prototype application.

- Centralize blue semantic Material 3 tokens in `theme/Color.kt` and wire them
  through `theme/Theme.kt`.
- Normalize shape and typography defaults in the existing theme.
- Update `PosShell` and navigation components for compact versus wider layouts.
- Make the Cashier catalog grid and cart presentation responsive.
- Replace visible amber wording in More with the blue accent wording.

The auth, opening, payment, history, return, closing, dialog, loading, error,
and recovery screens retain their layout work for the second refinement phase.
They receive the new colors automatically through the shared theme but have no
behavioral changes in this phase.

## Visual Tokens

`Color.kt` remains the sole custom color source. Existing Material `ColorScheme`
roles provide screen code semantic names rather than introducing per-screen
colors.

| Role | Value | Material role |
| --- | --- | --- |
| Primary action | `#5F7DF7` | `primary` |
| Pressed/strong action | `#4968EB` | `inversePrimary` / tonal use |
| Soft selected surface | `#E9EEFF` | `primaryContainer` |
| Page background | `#F7F8FC` | `background` |
| Main surface | `#FFFFFF` | `surface`, `surfaceContainerLowest` |
| Secondary surface | `#F2F4F8` | `surfaceContainerLow` |
| Alternate surface | `#EEF1F6` | `surfaceContainer` and higher containers |
| Primary text | `#1D1D1F` | `onSurface`, `onBackground` |
| Secondary text | `#6E6E73` | `onSurfaceVariant` |
| Muted border | `#E2E6EB` | `outlineVariant` |
| Success | `#4F9D8D` | `secondary` |
| Success surface | `#DFF4EE` | `secondaryContainer` |
| Error/refund | `#C84E5A` | `error` |
| Error surface | `#FDECEF` | `errorContainer` |

Warm warning remains a restrained semantic tertiary token only. Yellow and amber
are not used as a brand, selected, navigation, or primary-action color.

Theme shapes use 4dp, 8dp, 12dp, 16dp, and 28dp roles; cards resolve to 16dp,
and prominent controls resolve to 12dp. Existing Android system typography stays
in place to preserve font scaling support.

## Adaptive Shell

The shell determines layout from available Compose width.

- Compact (`<600dp`): existing bottom navigation; single content region.
- Medium (`600dp..839dp`): navigation rail; content uses remaining width and a
  centered max width where a screen needs it.
- Expanded (`>=840dp`): navigation rail; content has safe horizontal padding
  and avoids excessive stretch.

Only one navigation affordance is shown at a time. The selected item uses a
soft primary container and blue foreground. `safeDrawingPadding` continues to
be supplied by navigation roots, while shell content must apply scaffold padding
so system/navigation bars and IME do not conceal actionable content.

Routes, selected tab behavior, and callbacks remain unchanged.

## Cashier Workspace

Catalog filtering, search, cart mutation, customer selection, totals, session
state updates, and checkout callbacks remain exactly as they are.

- Compact: search, category filters, adaptive two-column minimum catalog, and
  existing View Cart CTA / modal cart sheet.
- Medium: use three or four catalog columns based on width. Cart stays modal to
  preserve sufficient catalog working space.
- Expanded: catalog and cart render side by side. The catalog gets roughly 60%
  to 65% of the content width; a 360dp to 420dp right panel contains selected
  customer, cart lines, totals, and the same checkout callback. No cart bottom
  sheet is opened in this mode.
- Product cards maintain a sensible minimum width rather than stretching to
  enormous tiles. The grid selects an adaptive minimum cell size with bounded
  content width.
- Low stock uses soft error-container treatment with readable error text;
  normal stock remains neutral. Product price and active quantity retain primary
  emphasis only where selection/action is communicated.
- CTA height stays 52dp compact and 56dp for wider working surfaces. The
  checkout action uses `primary` rather than amber or a primary container.

## More Surface

`MoreScreen` keeps all current information and actions. Its appearance row
reports `Blue` instead of `Amber` to match the actual theme. Close Shift retains
its existing callback and visual semantic unless its current screen marks it as
destructive.

## Accessibility and Quality

- Keep labels/status text with semantic colors; no status communicates by color
  alone.
- Preserve existing interactive labels and content descriptions; new navigation
  icons receive labels through Material navigation components.
- Keep controls at least 48dp where they are directly tappable.
- Prefer shared theme roles and spacing increments: 4, 8, 12, 16, 20, 24, 32dp.
- No new dependencies, no XML/View migration, and no backend/data changes.

## Verification

Run `:app:assembleDebug` and `:app:lintDebug` from
`prototype/android-prototype`. Install the APK on available API 25 emulator.

Verify screenshots or layout hierarchy for compact portrait, compact landscape,
medium width, and expanded width. Confirm nav switches once, catalog columns
adapt, expanded cashier retains a visible cart/checkout action, controls avoid
system bars, and no text clips at normal system font scale.

## Deferred Phase

The second phase applies layout refinement to auth, opening, payment, history,
return, more, closing, and their modal/error/recovery states. It uses the same
tokens and shell behavior established here, without modifying functional flows.
