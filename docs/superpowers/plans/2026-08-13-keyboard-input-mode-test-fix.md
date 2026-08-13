# Plan — Finish the keyboard input-mode instrumentation fix

## Context

Two instrumentation tests (`MoreScreenTest.appearance_controls_follow_external_keyboard_order`,
`CustomerSearchSheetTest.externalKeyboardTraversesResultLoadMoreRetryAndDoneAndActivatesSelection`)
failed when run in isolation and passed inside a full-package run. Root cause, verified with a
deterministic reproducer: a Compose node only holds focus while the window is in **keyboard input
mode**. A real touch inside the app's own window puts the window in touch input mode, where
`requestFocus()` is accepted but `Focused` reads `false` again immediately, so `assertIsFocused()`
fails. Input mode is device state, so any keyboard-traversal test that does not set it asserts
against whatever the device happened to be doing.

`app/src/androidTest/java/com/rotiropi/pos_erpnext/test/KeyboardInputMode.kt` was added on branch
`fix/keyboard-tests-touch-mode` (uncommitted at plan time) exposing
`enterKeyboardInputMode()`, which sends a real hardware `KEYCODE_TAB` via
`Instrumentation.sendKeyDownUpSync`. A real key event is what the platform treats as "the user
reached for a keyboard", so it switches the mode for every window — including the separate window a
`ModalBottomSheet` composes into, which `LocalInputModeManager` cannot reach because that API is
per-window. `Instrumentation.setInTouchMode` would be more direct but exists only from API 35, and
this suite also runs on API 25.

It is wired into `MoreScreenTest` and `CustomerSearchSheetTest`. Three further instrumentation
tests assert focus after `requestFocus()` and carry the identical latent defect; they pass today
only because an earlier test in the same package run already sent a key event.

## Spec

None. The authority for this work is `AGENTS.md` plus the Global Constraints below.

## Global Constraints

- Assertions are rewritten to resolve strings from resources, never weakened or deleted to make a
  failure disappear. If a step needs an assertion to become less strict, stop and report instead.
- Preserve every `testTag` value exactly.
- Do not change production code. This is a test-harness fix; `app/src/main` must be untouched.
- Do not rewrite the network layer, `auth/`, `data/`, `recovery/`, or `session/`.
- Never claim a gate passed that was not executed.
- Repository Markdown, code comments, test names, and commit messages in English.
- Do not push, publish, deploy, or merge without explicit user approval.

## Task 1 — Extend the input-mode guard to every focus-asserting instrumentation test

Three tests call `requestFocus()` and then assert focus, without entering keyboard input mode:

- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/ComposeShellTest.kt:140` — `requestFocus()`
  on `root-<first>`, then `assertIsFocused()` at line 142.
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/CashierScreenTest.kt:141` —
  `requestFocus().assertIsFocused()` on `cashier-search`, then `cashier-barcode` at line 143.
- `app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/customer/CustomerSearchRootTest.kt:232` —
  `requestFocus().assertIsFocused()` on `customer-search-input`, then six further
  `assertIsFocused()` calls through line 251.

For each: add `import com.rotiropi.pos_erpnext.test.enterKeyboardInputMode` and call
`enterKeyboardInputMode()` after `setContent` and before the first `requestFocus()`, matching the
placement already used in `MoreScreenTest.kt` and `CustomerSearchSheetTest.kt`.

`app/src/androidTest/java/com/rotiropi/pos_erpnext/ui/cashier/CatalogAccessibilityTest.kt:201`
calls `requestFocus()` but asserts nothing about focus afterwards, so it is deliberately left
alone.

No assertion changes. No `testTag` changes. No production changes. Imports stay alphabetically
ordered to match each file's existing convention.

## Task 2 — Host gate

Run from the repository root and capture real output:

```
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

AGP 9.2.1 does not expose `testReleaseUnitTest`; do not claim it ran. Record the unit-test count
and the result of each task.

## Task 3 — Instrumentation gate

On the attached emulator, at three window configurations, run the full `ui` package excluding the
special harness:

- 320x640 @160 (compact phone)
- 1080x1920 @420 (stock phone)
- 1600x2560 @320 (tablet)

Set each with `adb shell wm size WxH` + `wm density N` and reset with `wm size reset` /
`wm density reset` afterwards.

Then prove the fix is load-bearing with the isolated reproducer: launch `MainActivity`,
`input tap` inside the app window to force touch input mode, and run each of the five affected
test methods alone. Each must pass. Record the exact commands and the OK/FAILURE counts.

## Task 4 — Record the outcome

Update `docs/mobile-pos/` with the verification evidence: the root cause, the reproducer, the five
tests now guarded, and the gate results from Tasks 2 and 3. Correct any earlier record that
attributed these failures to IME state carried between tests — that diagnosis was wrong.
