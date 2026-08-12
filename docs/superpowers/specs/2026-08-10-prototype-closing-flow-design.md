# Prototype Closing Flow Design

## Scope

Replace the prototype closing bottom sheet with the latest Stitch closing flow:

1. `4.1 More - Ready to Close Shift`
2. `4.2 Closing Balance - Refined POS`
3. `4.3 Confirm Closing - Refined POS Flow`
4. `4.4 Shift Closed - Terminal Receipt`

Change only `prototype/android-prototype/`. Keep production `app/`, backend,
API contracts, persistence, authentication, idempotency, and accounting logic
unchanged. This is a prototype-only visual and navigation flow using fixture
data.

## Stitch References

Stitch project: `7730357639584129534` (`Roti Ropi Mobile POS`).

| Screen | Stitch screen ID |
| --- | --- |
| 4.1 More - Ready to Close Shift | `ce4aaa8984134cfd84007b00a7444526` |
| 4.2 Closing Balance - Refined POS | `18716b2f60c44ff78e05edbe57b771c4` |
| 4.3 Confirm Closing - Refined POS Flow | `3e9886f6984b4fc5bfdbd7f6c07dfeaf` |
| 4.4 Shift Closed - Terminal Receipt | `334991f94bad476fbeac5c278580b087` |

## Architecture and Navigation

Use explicit Navigation 3 keys and separate Composable screens:

```text
More -> ClosingBalance -> ConfirmClosing -> ShiftClosed -> More
```

- `MoreScreen` receives an `onCloseShift` callback instead of opening a local
  bottom sheet.
- `ClosingBalanceScreen` exposes `onBack` and `onReviewClosing`.
- `ConfirmClosingScreen` exposes `onBack`, `onEditAmounts`, and `onConfirm`.
- `ShiftClosedScreen` exposes `onDone`.
- Closing screens use a shared back-arrow/title top bar and no POS bottom
  navigation.
- `Done` pops the closing routes until the existing `More` route is visible.
- Remove obsolete `ConfirmClosingSheet`; screen 3.7 is replaced by 4.3.

## Screen Design

### 4.1 More ready to close

Keep current More layout and account/session fixture. `Close Shift` navigates to
4.2 rather than opening a `ModalBottomSheet`. Existing More tab navigation and
sign-out behavior remain unchanged.

### 4.2 Closing balance

Render a scrollable screen with a back arrow, `Closing Balance` title, wallet
summary, session details, reconciliation cards, counted amount inputs, shift
summary, and `Review Closing` action.

Use these Stitch fixtures:

- POS Profile: `Main Counter 01`
- Opening Ref: `REF-8492-A`
- Cashier: `Ahmad Rizky`
- Outlet: `Sudirman`
- Cash opening: `Rp 200.000`
- Cash expected: `Rp 865.000`
- QRIS opening: `Rp 0`
- QRIS expected: `Rp 350.000`
- Total expected: `Rp 1.215.000`
- Total counted: `Rp 1.210.000`
- Total difference: `-Rp 5.000`

Cash and QRIS counted fields start empty. `Review Closing` remains enabled and
does not validate or submit values in this prototype. The displayed shift
summary remains the fixed Stitch fixture and does not recalculate from those
empty fields.

### 4.3 Confirm closing

Render the refined confirmation surface over a closing-balance context. Include
the warning that the current Opening will be closed, cashier/profile/opening
details, Cash/QRIS/Vouchers summary, expected-counted table, total difference,
and `Confirm & Close Shift` action.

Use these Stitch fixtures:

- Cash in Drawer: `Rp 4.250.000`
- QRIS Total: `Rp 1.120.000`
- Vouchers: `Rp 150.000`
- Cashier: `Ahmad Rizky`
- POS Profile: `Main Counter 01`
- Opening Ref: `REF-8492-A`
- Cash expected and counted: `Rp 4.250.000`
- QRIS expected and counted: `Rp 1.120.000`
- Total expected and counted: `Rp 5.370.000`
- Total difference: `Rp 0`

Back and `Edit Amounts` return to 4.2. Confirm advances to 4.4.

### 4.4 Shift closed receipt

Render a terminal-style receipt with success status, Ref ID, receipt details,
invoice count, grand total, payment breakdown, balanced status, automated report
timestamp, and `Done` action.

Use these Stitch fixtures:

- Ref ID: `RR-20231027-04`
- Cashier: `Ahmad S.`
- POS Profile: `Main Counter - Roti Ropi`
- Opening Ref: `OB-99210`
- Invoice Count: `142 Invoices`
- Grand Total: `Rp 4.250.000`
- Cash: `1.250k` expected, counted, difference `0`
- QRIS: `3.000k` expected, counted, difference `0`
- Total Difference: `Rp 0`
- Status: `Shift Balanced`

`Done` returns to More. No receipt printer or server operation is added.

## Accessibility and Responsive Behavior

- Keep all actions at least 48dp high.
- Use meaningful back/action content descriptions.
- Keep 4.2 inputs keyboard-safe and all long receipt/summary content scrollable.
- Do not rely on red/green color alone for difference and balanced states.
- Keep text and totals readable on the existing low-end mobile emulator.

## Verification

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Install the APK and verify the complete click path:

1. More -> `Close Shift` opens 4.2.
2. Empty counted fields remain editable and `Review Closing` opens 4.3.
3. Back and `Edit Amounts` return to 4.2.
4. `Confirm & Close Shift` opens 4.4.
5. `Done` returns to More.
6. Capture screenshots and inspect UI dump text for 4.1 through 4.4.

The existing prototype unit/instrumentation test scaffold references missing
`com.example.posprototype` symbols. Do not modify that unrelated scaffold for
this visual prototype flow; report its failure separately if it remains.
