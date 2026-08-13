# Prototype Grand Total Font Design

## Goal

Reduce the Grand Total amount size in the narrow cart layout so the amount does not visually overwhelm the summary or payment button.

## Scope

- Change only the narrow-screen Grand Total amount in `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`.
- Keep the existing two-row layout on narrow screens.
- Keep the wide-screen layout, total calculation, labels, and checkout behavior unchanged.

## Design

Use the existing `PriceDisplay` typography with a smaller explicit font size and line height in the `maxWidth < 360.dp` branch. Keep the amount right-aligned and full-width below the `Grand Total` label.

## Verification

- Compile and assemble the prototype debug APK.
- Run lint.
- Open cart on the API 25 emulator and confirm the smaller amount does not overlap the summary or payment button.
