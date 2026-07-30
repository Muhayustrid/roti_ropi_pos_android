# Task 2D Static Cashier Surfaces Design

## Goal

Add honest, static Compose surfaces for Cashier, cart, exact-settlement checkout, and terminal receipt. Preserve future Tasks 7–9 boundaries: no runtime catalog, quote, payment, or sale orchestration.

## Scope

- Cashier search and manual/HID barcode input.
- Category chips and adaptive product grid.
- Empty and populated cart visuals with quantity controls and a 50-row bound.
- Compact layout with floating cart summary and modal bottom sheet.
- Expanded layout with persistent cart pane.
- Checkout states: unavailable, offline-not-submitted, price changed, submitting, and error.
- Receipt surface based only on terminal server-shaped data, including server-provided change.
- Representative light, dark, compact, expanded, and font-scale 1.5 debug previews.

## Boundaries

- Release runtime uses an honest unavailable state; synthetic records exist only in debug previews and tests and display `Demo data`.
- Prices and stock display server-snapshot labels.
- Checkout confirmation remains disabled until future authoritative payable and server payment modes exist.
- No authoritative subtotal, tax, payable, or change calculation.
- No overpayment, editable discounts, camera scanning, printer, sync, endpoint, repository, DTO, recovery, or dependency work.

## Architecture

Use feature-local immutable visual models:

- `CashierUiState` owns product, search, category, cart, and checkout visual state.
- `CashierScreen` emits events and chooses compact or expanded composition.
- Focused composables render product browsing, cart content, checkout, and receipt.
- `PosShell` replaces only the Cashier placeholder and supplies the release unavailable state.
- Debug preview fixtures supply bounded demo states without entering release runtime.

Do not extract private Products feature components during this task. Reuse theme tokens and existing public primitives only. This keeps the Task 2C surface unchanged and avoids coupling read-only Products models to future quote/cart behavior.

## Data Flow

`PosShell` passes immutable state to `CashierScreen`. Composables render state and emit callbacks such as search change, category selection, barcode submission, quantity change, cart open/close, retry, and receipt close. Task 2D callbacks remain visual-only; no repository or network operation handles them.

Compact mode exposes cart summary before opening a modal sheet. Expanded mode renders the same cart state in a persistent pane. Receipt and checkout states replace cart content without inventing mutation outcomes.

## Accessibility and Input

- Minimum 48 dp action targets.
- Semantic headings, button roles, selected states, progress, errors, and disabled confirmation state.
- Error state uses a live region.
- Logical TalkBack and external-keyboard order: search, barcode, categories, products, cart, checkout actions.
- Barcode field supports manual typing and HID scanner submission; camera controls remain absent.
- Font scale 1.5 preserves primary actions, cart summary, and receipt totals without clipping.

## Testing

TDD sequence:

1. Add state-model unit tests for 50-row bounding, demo/snapshot labels, disabled confirmation, and terminal receipt values.
2. Add Compose tests for compact/expanded layouts, cart sheet/pane, checkout states, semantics, touch targets, font scale, keyboard/scanner focus, and release honesty.
3. Replace obsolete shell assertion that no scanner input exists with Cashier-specific scope assertions.
4. Add release-fixture exclusion coverage if current test does not cover new preview fixtures.
5. Run focused tests, full available Gradle gate, API 23/API 36 device suites, and representative preview inspection.

## Files

Expected additions:

- `app/src/main/java/com/rotiropi/pos_erpnext/ui/cashier/`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/payment/`
- `app/src/main/java/com/rotiropi/pos_erpnext/ui/receipt/`
- `app/src/debug/java/com/rotiropi/pos_erpnext/ui/preview/`
- focused unit and instrumentation tests

Expected modifications:

- `app/src/main/java/com/rotiropi/pos_erpnext/ui/navigation/PosShell.kt`
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt`
- `app/src/test/java/com/rotiropi/pos_erpnext/ui/ReleaseFixtureExclusionTest.kt` only if needed

## Completion Gate

Task 2D is complete only when focused and full available Gradle checks pass, API 23/API 36 device tests pass, representative previews are inspected, accessibility/input behavior is verified, and targeted diff review finds no runtime integration or unsupported claim. Stop before Task 2E. Commit and push require separate user approval.
