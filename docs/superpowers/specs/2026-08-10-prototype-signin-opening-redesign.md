# Prototype Sign-In to Opening Redesign

## Scope

Refresh the prototype Android surfaces for Stitch screens 1.2 through 1.5:

- `Login - Waiting State`
- `Opening Balance`
- `Confirm Opening Balance - Redesign`
- `Opening - Recovery State`

Keep the approved prototype `Secure Sign In` screen as the 1.1 baseline. Change
only the prototype under `prototype/android-prototype/`; do not change the
production `app/` module, backend, API contracts, authentication, persistence,
or accounting behavior.

## Reference Screens

Stitch project: `7730357639584129534` (`Roti Ropi Mobile POS`).

| Screen | Stitch screen ID |
| --- | --- |
| 1.2 Login - Waiting State | `804fee3435cf4e6db85f40d85a166fdb` |
| 1.3 Opening Balance | `f4fe0e61b28b49bcb88bffa32e74d829` |
| 1.4 Confirm Opening Balance - Redesign | `bbce56b6ed384b6a8fb77bc16aae5353` |
| 1.5 Opening - Recovery State | `d193685e19be40a4a78336c992e85b92` |

## Design

### 1.2 Login waiting

Retain the disabled secure sign-in form as the background context. Add the
Stitch-style scrim and centered waiting surface with the sync indicator,
`Waiting for ERPNext sign-in...` copy, browser-authentication explanation, and
`Cancel` action. Keep the existing `onAuthSuccess` delay and `onCancel`
callback unchanged.

### 1.3 Opening balance

Use a pre-shift page layout with a simple title header, wallet hero, explanatory
copy, session details, opening funds inputs, total opening summary, and a sticky
`Start Shift` action. Remove `PosBottomBar`; it is not part of the pre-shift
Stitch surface and currently has no active callback on this screen.

Preserve the existing editable Cash value, disabled QRIS value, Rupiah
formatting, total calculation used by the prototype, `onConfirm`, and
`onLogout` callbacks.

### 1.4 Confirm opening balance

Keep the confirmation surface as a scrimmed, scroll-safe modal over the opening
flow. Match the latest Stitch hierarchy: warning message, session details,
opening funds breakdown, total opening summary, primary `Confirm & Start Shift`
button, and outlined `Edit Amounts` button.

Use latest Stitch fixture values:

- Cashier: `John Doe`
- Profile: `Admin`
- Outlet: `Downtown Store`
- Currency: `IDR`
- Cash: `Rp 200.000`
- QRIS: `Rp 0`

Keep `onRetry` as confirm action and `onDismiss` as edit action so navigation
behavior remains unchanged.

### 1.5 Opening recovery

Replace the minimal recovery body with the Stitch-style branded status surface:
brand header with account affordance, centered sync/progress indicator, heading
`Checking Shift Status`, status copy, and `Starting Shift...` pending state.
Keep the current `onRetry` callback and simulated delay. Do not add a new retry
policy or network behavior.

## Implementation Boundaries

Edit only:

- `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/auth/LoginWaitingScreen.kt`
- `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/OpeningBalanceScreen.kt`
- `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/ConfirmOpeningScreen.kt`
- `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/OpeningRecoveryScreen.kt`

Reuse existing Material 3 primitives and theme tokens. Do not add dependencies,
new navigation keys, new state hosts, or shared abstractions unless required
by compilation or accessibility.

## Accessibility and Responsive Behavior

- Keep interactive targets at least 48dp.
- Preserve visible labels and sufficient contrast for disabled fields and scrims.
- Keep opening and confirmation content vertically scrollable on narrow phones.
- Keep action controls reachable above system bars and the keyboard.
- Provide meaningful content descriptions for actionable icons; decorative icons
  remain excluded from accessibility traversal.

## Verification

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Install the debug APK on the available emulator and verify:

1. Sign in reaches waiting state; Cancel returns to sign-in.
2. Waiting state reaches opening balance after existing simulated delay.
3. Cash remains editable, QRIS remains disabled, and Start Shift opens confirmation.
4. Confirm action reaches recovery; Edit Amounts returns to opening.
5. Recovery reaches the existing next screen through its callback.
6. Capture screenshots for screens 1.2 through 1.5 and inspect UI dump text.

The existing prototype unit-test scaffold may fail because it references missing
`com.example.posprototype` symbols. Report that failure separately instead of
changing unrelated test scaffolding in this redesign.
