# Prototype Return Flow Design

## Scope

Add a working prototype return flow from successful transaction details:

```text
Transaction Detail -> Return Sale -> Return Submitted -> History
```

Change only `prototype/android-prototype/`. Keep production `app/`, backend,
Mobile POS API contracts, persistence, accounting, quote, validation, and
recovery behavior unchanged. This flow is local UI state with fixture data.

## Existing Entry Point

`TransactionDetailScreen` already renders `Start Return` for successful
transactions and accepts `onStartReturn`; Navigation currently leaves that
callback at its no-op default. Wire that callback to the new `Return` route.
Refunded and draft transaction detail states remain unchanged.

## Navigation and Screens

Add two serializable Navigation 3 keys:

- `Return(transactionId: String)`
- `ReturnSuccess(transactionId: String)`

Add two prototype screens under `ui/returning/`:

- `ReturnScreen.kt`
- `ReturnSuccessScreen.kt`

`ReturnScreen` exposes `onBack` and `onSubmit`. `ReturnSuccessScreen` exposes
`onBack` and `onDone`. Back from the form returns to the original Transaction
Detail. Done from success pops the return routes until History is visible.

## Return Sale Screen

Use a scrollable, keyboard-safe screen with a back/title header and sticky
submit action.

### Source transaction fixture

- Transaction: `#TRX-9402`
- Date/time: `Oct 24, 2026 • 14:20`
- Customer: `Walk-in Customer`
- Original total: `Rp 155.000`
- Payment: `Paid with QRIS`

### Item fixtures

Use local return item state derived from the successful transaction fixture:

- `Roti Manis`, original quantity `2`, unit price `Rp 12.000`, return quantity `1`
- `Croissant`, original quantity `3`, unit price `Rp 18.500`, return quantity `0`
- `Pain au Chocolat`, original quantity `2`, unit price `Rp 22.000`, return quantity `0`

Each item shows original quantity, editable return quantity, and line refund
amount. Quantity edits update local refund summary using `quantity * unitPrice`.

### Return controls

- Reason field defaults to `Damaged Item`.
- Refund mode defaults to `Cash`; provide `Cash` and `QRIS` selectable chips.
- Summary shows selected item count and refund total `Rp 12.000` initially.
- `Submit Return` is enabled for prototype demonstration and directly opens
  Return Submitted without server validation.

## Return Submitted Screen

Use a success receipt-style layout with scrollable content:

- Heading: `Return Submitted`
- Status: `Submitted`
- Ref ID: `RET-TRX-9402-01`
- Original transaction: `#TRX-9402`
- Customer: `Walk-in Customer`
- Refund amount: `Rp 12.000`
- Refund mode: `Cash`
- Returned item: `Roti Manis x1`
- Primary action: `Done`

Use visible text and icon state so success does not rely only on color.

## Accessibility and Responsive Behavior

- Use 48dp minimum action and chip targets.
- Keep quantity fields numeric and keyboard-safe.
- Keep item cards and success receipt vertically scrollable on narrow screens.
- Use meaningful back/action content descriptions.
- Keep refund amount and selected quantity readable at large font scales.

## Verification

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Install the debug APK and verify:

1. Open History, open successful `#TRX-9402`, and tap `Start Return`.
2. Confirm Return Sale screen shows source transaction, three items, default
   quantity/reason/refund mode, and `Rp 12.000` summary.
3. Change quantity and confirm refund summary updates.
4. Tap `Submit Return` and confirm Return Submitted fixture.
5. Tap `Done` and confirm History is visible.
6. Use UI dump and screenshots for Return Sale and Return Submitted.

The existing prototype unit/instrumentation test scaffold references missing
`com.example.posprototype` symbols. Do not change that unrelated scaffold for
this prototype flow; report its failure separately if it remains.
