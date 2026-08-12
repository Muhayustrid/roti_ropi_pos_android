# Prototype Payment Change Design

## Goal

Allow cash overpayment in the prototype and clearly show the resulting change on both Payment Entry and its confirmation dialog.

## Scope

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentCashScreen.kt`.
- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/payment/PaymentConfirmationDialog.kt`.
- Keep exact-payment behavior, underpayment validation, keypad input, quick amounts, and navigation unchanged.
- Do not change production `app/` code or add dependencies.

## Payment Rules

- `remaining = max(grandTotal - cashReceived, 0)`.
- `change = max(cashReceived - grandTotal, 0)`.
- Payment can complete when `cashReceived >= grandTotal`.
- Underpayment remains disabled.
- Show `Change (Kembalian)` row only when `change > 0`.
- Pass actual `change` through existing `onConfirm(cashReceived, change)` callback.

## UI Design

- Payment Entry summary shows `Change (Kembalian)` in primary color below Remaining when overpaid.
- Confirmation dialog shows same change row only for overpaid cash payments.
- `PaymentConfirmationDialog.change` defaults to `0L`, preserving QRIS, card, and split-payment callers.

## Verification

- Compile and assemble the prototype debug APK.
- Run lint.
- Install and launch on API 25.
- Verify underpayment keeps Complete Payment disabled.
- Verify exact payment enables Complete Payment with no change row.
- Verify overpayment shows Remaining `Rp 0`, a Change row, enables Complete Payment, and forwards the actual change value.
