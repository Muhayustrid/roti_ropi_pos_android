# Prototype Responsive Cart Summary Design

## Goal

Make the cart summary adapt to available width so the subtotal, tax, Grand Total, spacing, and checkout CTA remain balanced on narrow API 25 screens while preserving the Stitch presentation on wider screens.

## Scope

- Change the cart summary footer in `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/cashier/CashierScreen.kt`.
- Use width-based layout tokens for compact, regular, and wide content widths.
- Keep item totals, tax calculation, Grand Total calculation, and checkout behavior unchanged.
- Do not change production `app/` code or add dependencies.

## Responsive Design

Use `BoxWithConstraints` around the summary content and select three width tiers:

- Compact `<360.dp`: reduce summary padding and vertical spacing, use smaller label/value styles, render Grand Total around `24.sp` with `30.sp` line height, and use a `48.dp` checkout button.
- Regular `>=360.dp and <600.dp`: use intermediate spacing and typography.
- Wide `>=600.dp`: preserve existing Stitch spacing, typography, and `56.dp` checkout button.

Keep Grand Total label and amount on two rows in compact mode. Keep the existing wide layout behavior. All monetary values remain right-aligned where they are today.

## Verification

- Compile and assemble the prototype debug APK.
- Run lint.
- Install and launch on the API 25 emulator.
- Open cart at 320dp and confirm summary content and CTA do not overlap or feel oversized.
- Confirm wide branch source remains unchanged and calculations are identical.
