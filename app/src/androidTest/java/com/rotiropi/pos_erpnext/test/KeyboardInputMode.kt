package com.rotiropi.pos_erpnext.test

import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Puts every window into keyboard input mode, so a test may assert what `Tab` focuses.
 *
 * A Compose node only holds focus while the window is in keyboard input mode. A real touch
 * inside the app's window — the previous test clicking something, a tap on the device — puts
 * the window in touch input mode, and there `requestFocus()` is accepted but `Focused` reads
 * `false` again immediately, so `assertIsFocused()` fails against a node whose `Actions` still
 * list `RequestFocus`.
 *
 * That is what made the external-keyboard tests look like flakes. They passed inside a
 * full-package run, where an earlier test's key event had already switched the mode, and failed
 * when driven alone right after a touch. Input mode is per-window state, so a keyboard-traversal
 * test that does not set it is asserting against whatever mode that window happened to be in.
 *
 * A Compose approach using `LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)`
 * fixed `MoreScreenTest` but not `CustomerSearchSheetTest`, because `ModalBottomSheet` composes
 * into a separate window with its own `InputModeManager`; input mode is per-window. A real hardware
 * key event via `sendKeyDownUpSync` switched the mode for both windows.
 * `Instrumentation.setInTouchMode(Boolean)` is available on all supported API levels, but its
 * behavior against the second window was not measured here; measure it before replacing this helper.
 *
 * Call it after `setContent`, before the first `requestFocus()` and after any click or touch
 * performed by the test itself.
 */
fun enterKeyboardInputMode() {
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
}
