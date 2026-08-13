# Prototype Responsive Payment Entry Design

## Goal

Make the cash payment entry screen readable and balanced on narrow screens without changing payment calculations, validation, or confirmation behavior.

## Scope

- Change `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt`.
- Use width-based layout tokens for compact, regular, and wide content widths.
- Keep cash input parsing, remaining amount, exact-payment validation, overpayment message, and confirmation dialog behavior unchanged.
- Do not change production `app/` code or add dependencies.

## Responsive Design

Use `BoxWithConstraints` around the scrollable payment content and select three width tiers:

- Compact `<360.dp`: keep the title on one line with smaller typography, reduce horizontal/vertical spacing, use smaller amount and keypad typography, wrap quick amounts into rows of three and two, and use a `48.dp` Complete Payment button.
- Regular `>=360.dp and <600.dp`: use intermediate spacing and typography; quick amounts wrap when available content is still narrow.
- Wide `>=600.dp`: preserve the existing Stitch spacing, typography, one-row quick amounts, and `56.dp` button.

The payment content scrolls vertically when height is insufficient. Complete Payment remains outside that scroll region and stays accessible at the bottom.

## Behavior Preservation

- `cashReceived`, `remaining`, `isExact`, and `isOverpaid` expressions remain unchanged.
- Quick amount values retain current values and input behavior.
- Keypad keys retain current input and delete behavior.
- Confirmation dialog and `onConfirm` callback remain unchanged.

## Verification

- Compile and assemble the prototype debug APK.
- Run lint.
- Install and launch on the API 25 emulator.
- Open cash payment at `320dp` and confirm title, quick amount rows, keypad, and CTA do not overlap or clip.
- Manually inspect regular and wide width branches for preserved layout behavior.
