# Keyboard input mode instrumentation verification

- **Evidence ID:** `keyboard-input-mode-test-fix`
- **Android commits:** `109ee53` (guard, 6 files, +39/-0); `52beda9` (KDoc correction, comment-only, +6/-4)
- **Scope:** Compose instrumentation focus assertions after device or app touch input
- **Operator:** Android repository verification

## Root cause

A Compose node only holds focus while the app window is in keyboard input mode. A real touch inside the app's own window switches the window to touch input mode; in that mode `requestFocus()` is accepted, but `Focused` reads `false` again immediately, so `assertIsFocused()` fails even though the node still exposes `RequestFocus`.

The earlier diagnosis that these failures were caused by IME state carried between tests was wrong. The actual inherited state was the window's keyboard input mode. A key event through `com.rotiropi.pos_erpnext.test.enterKeyboardInputMode()` establishes the required mode before the first focus request. Input mode is per-window, and the helper's hardware key event also covers the `ModalBottomSheet` window.

## Guarded tests

The guard is present in all five tests that call `requestFocus()` and then assert focus:

- `com.rotiropi.pos_erpnext.ui.ComposeShellTest.external_keyboard_traverses_root_destinations_in_visual_order`
- `com.rotiropi.pos_erpnext.ui.CashierScreenTest.external_keyboard_moves_from_search_to_barcode`
- `com.rotiropi.pos_erpnext.ui.MoreScreenTest.appearance_controls_follow_external_keyboard_order`
- `com.rotiropi.pos_erpnext.ui.customer.CustomerSearchSheetTest.externalKeyboardTraversesResultLoadMoreRetryAndDoneAndActivatesSelection`
- `com.rotiropi.pos_erpnext.ui.customer.CustomerSearchRootTest.productionRootExternalKeyboardSelectsLoadsReachesRetryAndDismisses`

## Host gate

Command run at `52beda9`:

```text
./gradlew --no-daemon testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest
```

Result: `BUILD SUCCESSFUL` in 41 seconds, exit code 0. The unit-test result was 511 tests, 0 failures, 0 errors, and 0 skipped across 61 classes. The log had no warning, error, or failure marker relevant to the gate.

This is the honest scope of the pass: `assembleDebug` and `lintRelease` reported `UP-TO-DATE` or partially up-to-date because an earlier run had already produced those outputs from the same androidTest-only source state. The claim is therefore that the gate is green at `52beda9`, not that every artifact was rebuilt from scratch at that commit. `testReleaseUnitTest` was not run and is not claimed; AGP 9.2.1 does not expose that task.

## Instrumentation gate

The full `com.rotiropi.pos_erpnext.ui` package passed with `OK (95)` at each tested window:

| Window | Result |
| --- | --- |
| 320x640 @160 | OK (95), 0 failures, 0 errors |
| 1080x1920 @420 | OK (95) |
| 1600x2560 @320 | OK (95) |

Window overrides were reset and verified afterwards at 1080x1920 @420. Phone landscape 2400x1080 @420 was deliberately not tested: the product decision is landscape on tablet and portrait on phone.

## On-device reproducer and negative control

The reproducer was armed by launching the app's own `MainActivity`, sending a real touch inside that window, and verifying `mInTouchMode=true` before dispatching each isolated test. With the guard in place, all five methods passed when run alone.

The guard-removed control was rebuilt, reinstalled, and run armed at 1080x1920 @420. The install pipeline was proved live before trusting the control passes: the Cashier assertion tag was temporarily replaced with `deploy-canary-must-not-exist`; after rebuild and reinstall, the run failed because no node with that tag existed. An edit therefore reached the device, so a guard-removed pass below was not a stale APK.

| Test | Guard removed, armed | Verdict |
| --- | --- | --- |
| `MoreScreenTest#appearance_controls_follow_external_keyboard_order` | `AssertionError: Failed to assert (Focused = 'true')` — twice, back to back | Guard load-bearing |
| `CustomerSearchSheetTest#externalKeyboard...` | `AssertionError: Failed to assert (Focused = 'true')` | Guard load-bearing |
| `CustomerSearchRootTest#productionRootExternalKeyboard...` | `AssertionError: Failed to assert (Focused = 'true')` | Guard load-bearing |
| `CashierScreenTest#external_keyboard_moves_from_search_to_barcode` | `OK (1 test)` | Guard not load-bearing here |
| `ComposeShellTest#external_keyboard_traverses_root_destinations_in_visual_order` | `OK (1 test)` | Guard not load-bearing here |

The guard is load-bearing for three of the five tests today. The other two pass without it on API 36 in this window: they focus `OutlinedTextField`s (`cashier-search`) or `NavigationBarItem`s in the main window; `ComposeShellTest` also calls `activity.window.decorView.requestFocusFromTouch()` itself. The three failing tests focus non-editable nodes (`FilterChip`s and `customer-search-input` inside `CustomerSearchSheet`'s separate window), which explains the split. This is an observed split for this device and window, not evidence that the other two guards are unnecessary.

## Decision

Keep the guard on all five tests. It is one idempotent line, and its absence is a latent, device-state-dependent failure: passing today on API 36 in this window does not mean a test cannot fail on another device or after a different preceding interaction. The cost is two no-op calls in tests that currently pass without it; trimming them would risk rediscovering the same inherited-window-state defect.

## Cleanup and limits

Window overrides were reset and verified after instrumentation. The `app/src/androidTest` sources were restored and verified clean after each control, with no control committed. Untracked files elsewhere were deliberately left in place. No release unit-test claim is made because `testReleaseUnitTest` is not exposed by AGP 9.2.1. No claim is made that every host artifact was rebuilt from scratch.
