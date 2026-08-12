# Prototype Responsive Payment Confirmation Design

## Goal

Reduce oversized typography and spacing in the Complete Payment dialog on narrow screens while preserving its content and actions on wider screens.

## Scope

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt`.
- Use width-based compact and wide visual tokens.
- Keep customer, method, change, total, item data, confirm callback, and back callback unchanged.
- Do not change production `app/` code or add dependencies.

## Responsive Design

Use `BoxWithConstraints` around the dialog surface:

- Compact `<360.dp`: surface padding `16.dp`, icon `40.dp`, title `titleLarge`, subtitle `bodyMedium`, total `headlineSmall`, detail card padding `12.dp`, detail label/value `bodySmall/bodyMedium`, and action buttons `48.dp` with compact typography.
- Wide `>=360.dp`: preserve current `24.dp` padding, icon `48.dp`, current headline/body styles, detail padding `16.dp`, and `48.dp` action buttons.

Compact detail rows use balanced label/value weights so `Cash (Rp 100.000)` and `Change` remain readable without awkward wrapping. Dialog content and actions remain functionally identical.

## Verification

- Compile and assemble the prototype debug APK.
- Run lint.
- Install and launch on API 25.
- Open Complete Payment at `320dp` and verify title, subtitle, total, detail rows, Change, and buttons fit without oversized text or clipping.
- Confirm wide branch source retains current visual tokens and callbacks compile unchanged.
