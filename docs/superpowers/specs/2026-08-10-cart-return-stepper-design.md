# Cart and Return Stepper Design

## Scope

Make Return quantity input match Cashier cart interaction and repair Cashier
cart row layout:

- Cashier cart uses a reusable `- quantity +` stepper.
- Return item cards use the same stepper instead of a text field.
- Cart rows remain easy to scan and do not clip totals, names, remove action, or
  quantity controls.
- Use existing product placeholder initials; add no image assets or dependency.

Change only `prototype/android-prototype/`. Keep checkout, payment totals,
return totals, navigation, backend, and accounting behavior unchanged.

## Shared Component

Create `ui/components/QuantityStepper.kt`:

```kotlin
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
)
```

Render a rounded pill with 48dp minimum touch targets for decrease/increase,
centered quantity, and content descriptions `Decrease quantity` and
`Increase quantity`. Disable decrease at `min` and increase at `max`.

## Cashier Cart

Update `CartItemRow` in `CashierScreen.kt` to use `QuantityStepper`:

- Keep 64dp placeholder thumbnail and initials.
- Keep product name, `qty x Rp price`, and Remove action in flexible middle area.
- Keep line total and stepper in stable trailing area.
- Use separators/spacing so item rows do not collide inside the bottom sheet.
- Cart decrease keeps existing behavior: quantity 1 removes item; higher quantity
  decrements. Increase increments cart quantity.
- Keep subtotal, tax, grand total, and `Continue to Payment` unchanged.

## Return Items

Update `ReturnLine` in `ReturnScreen.kt` from string quantity to integer quantity:

- `Roti Manis` default quantity `1`, original quantity `2`.
- `Croissant` default quantity `0`, original quantity `3`.
- `Pain au Chocolat` default quantity `0`, original quantity `2`.

Replace `OutlinedTextField` with the shared stepper. Clamp quantity to
`0..originalQuantity`; minus at zero keeps card visible. Recompute line refund,
selected item count, summary, and sticky refund total from stepper state.
Keep reason, refund mode, submit flow, and success screen unchanged.

## Accessibility and Responsive Behavior

- Stepper controls have 48dp minimum touch targets.
- Cart and Return content remain vertically scrollable.
- Long product names use flexible middle space and ellipsis where needed.
- Trailing totals remain visible at large font scales.
- Decorative icons use null content descriptions; actionable controls are labeled.

## Verification

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Verify on emulator:

1. Add Roti Manis and Croissant, open Your Cart, and inspect row alignment.
2. Test cart plus/minus, Remove, subtotal/tax/grand total, and Continue to Payment.
3. Open successful `#TRX-9402`, tap Start Return, and inspect stepper rows.
4. Verify return quantities start `1/0/0`, clamp at original quantities, and
   update refund total.
5. Capture cart and Return Sale screenshots and inspect UI dump text.

The existing prototype unit/instrumentation scaffold references missing
`com.example.posprototype` symbols. Do not change that unrelated scaffold; report
its failure separately if it remains.
