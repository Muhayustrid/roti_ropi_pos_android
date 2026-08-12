# Opening Amount Field Design

## Scope

Improve Opening Session presentation and amount editing in the existing native Jetpack Compose flow. Add an isolated `OpeningAmountField` and integrate Layout A's single summary surface into `OpeningScreen`.

Preserve existing Android behavior, Task 6 contracts, server-provided payment modes and order, non-editable mode policy, canonical decimal validation, durable mutation persistence, original UUID/body replay, recovery state, authentication generation isolation, reconciliation, and capability refresh.

Do not change backend code, DTOs, request serialization, request body format, UUID generation, recovery transport, persistence, reconciliation, global theme, unrelated screens, or Task 7.

## Design decisions

### Editing ownership

`OpeningAmountField` owns internal `TextFieldValue`, including selection and cursor state. Its public callback emits only an ungrouped editable value. The ViewModel never receives visual selection, grouping separators, or transformed offsets.

### Three representations

1. Editable raw value: ungrouped input such as `10000`, `10000,50`, or `10000.50`.
2. Display value: Indonesian presentation such as `10.000` or `10.000,50`.
3. Canonical submit value: existing domain output such as `10000.00` or `10000.50`.

Raw input remains unscaled during editing. Existing `canonicalizeOpeningAmount` remains the only canonical submit authority.

### Visual transformation

`VisualTransformation` inserts grouping separators, renders decimal separator as comma, and supplies bidirectional offset mapping based on numerical positions. It does not validate minimum, apply scale, round, truncate, mutate persistence, or call domain code.

### Input normalization

Presentation-layer editing accepts ungrouped decimal input with either `,` or `.` as decimal separator. It also accepts structurally valid Indonesian grouping on paste and normalizes it before callback:

- `1.000` → `1000`
- `10.000,50` → `10000,50`
- `1.000.000,25` → `1000000,25`

Malformed grouping, mixed syntax, signs, exponent syntax, whitespace, and symbols are rejected safely. Clearing and trailing decimal input remain editable intermediate states; existing validation controls submission.

### Zero and editing behavior

A server suggested zero displays as `0`. Focus selects the full zero when practical. The first digit replaces zero, so `0` plus `1` becomes raw `1` and displays `1`. Backspace removes the intended numeric digit even when adjacent to a visual grouping separator. Typed fractional digits and trailing decimal separators remain intact while editing.

### Native UX

Use Material 3 primitives, existing `PosTheme`, semantic theme tokens, visible labels, `KeyboardType.Decimal`, 48dp minimum touch targets, inline supporting errors, live-region announcements, predictable focus order, scroll-safe layout, and font-scale-safe typography. Keep Layout A's summary as contextual “Opening amounts”, never as a client-calculated accounting total.

## UI UX Pro Max classification

### Adopt now

- Visible labels and inline errors.
- Decimal keyboard with support for both comma and dot input.
- Selection-aware cursor behavior.
- 48dp touch targets and 8dp spacing rhythm.
- Semantics for field labels, locked state, errors, loading, and recovery.
- Scrollable narrow-phone and large-font layout.
- Disabled state clarity without color-only communication.
- Light/dark contrast through existing theme tokens.

### Adapt

- Next.js blue/pale-blue surface relationships mapped to existing Material 3 `PosTheme`.
- HomeScreen hierarchy combined with PaymentScreen amount/error/recovery patterns.
- Single summary surface interpreted as context, not authoritative total.
- Web form appearance translated to native `OutlinedTextField`, `Surface`, and `Button`.

### Defer

- Global design-system rewrite.
- Dashboard, cart, checkout, products, customers, reports, settings, navigation, and snackbar parity.
- Broader icon and shell parity.

### Reject

- React, Tailwind, shadcn, browser input, or Next.js routing translation.
- Local Float/Double conversion, rounding, truncation, or canonical mutation.
- Any Task 6 transport, persistence, UUID, DTO, request, recovery, or reconciliation change.

## Planned files

### Production

- `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountField.kt`: internal `TextFieldValue`, raw normalization, display transformation, offset mapping, keyboard and semantics.
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/opening/OpeningScreen.kt`: Layout A integration, summary surface, field integration, responsive and state presentation.

### Tests

- `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountFieldTest.kt`: raw normalization, grouping, decimal preservation, trailing separator, paste rejection, offset mapping, backspace semantics, exact large values.
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningAmountCanonicalizerTest.kt`: preserve and extend existing canonical ASCII decimal-dot, minimum, scale, and no-rounding coverage.
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/opening/OpeningViewModelTest.kt`: non-editable mode, recovery blocking, canonical request body without grouping, unchanged opening success/recovery behavior.
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/opening/OpeningScreenTest.kt`: zero display, grouped amount, focus, disabled submit, errors, recovery, locked mode, narrow layout, large font scale.

### Debug previews

- `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/OpeningPreviews.kt`: zero, grouped, decimal, invalid, minimum error, locked, submitting, recovery, dark, large font, compact phone, and landscape fixtures.

## Testing and verification

Use TDD: add focused failing tests before production edits, run focused JVM tests, implement minimum behavior, run Compose tests, then run repository gates. Prove numeric safety with exact large-value tests and request serialization assertions, not source-text searches for `Float` or `Double`.

Run:

- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest`
- `git diff --check`

Run available opening instrumentation checks on API 23, API 25, and API 36. Compare rendered Android Opening UI with Next.js reference at 360×800, 412×915, and supported landscape dimensions. Verify light/dark, font scale, accessibility semantics, and safe scrolling.

External response-drop rerun is not required only if production diff remains editing/presentation-only and tests prove canonical request JSON is unchanged, persisted body has no grouping, UUID generation is unchanged, and durable recovery/replay/reconciliation code is untouched. If any such code changes, stop and report that rerun is required.
