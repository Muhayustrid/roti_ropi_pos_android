package com.rotiropi.pos_erpnext.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measured window the UI is currently laid out in.
 *
 * Layout decisions read the real available size from here instead of a device
 * preset, so a rotation, a fold, and a split-screen resize all take the same code
 * path with no `isTablet` flag anywhere. One `BoxWithConstraints` at the shell root
 * measures once and publishes through [LocalPosWindow].
 *
 * This complements, and does not replace,
 * [com.rotiropi.pos_erpnext.ui.navigation.posLayoutModeForWidth]: that function
 * remains the authority on width, deciding grid column counts and whether a screen
 * offers an expanded layout at all. [isTall] adds the height half of that decision,
 * which width alone cannot express.
 */
@Immutable
data class PosWindow(
    val width: Dp,
    val height: Dp,
) {
    /**
     * True when the window is tall enough for two full-height body columns.
     *
     * Width alone is not enough to justify splitting a body. A landscape phone is
     * wide (~914dp) but only ~411dp tall, and two full-height columns there clip
     * both halves instead of fitting them, so such a window stacks and scrolls
     * like a compact one.
     */
    val isTall: Boolean get() = height >= TALL

    companion object {
        /** Height a window needs before two full-height columns are legible. */
        val TALL = 600.dp
    }
}

/**
 * Default is a portrait-phone-sized window that is tall enough for a split body, so
 * a composable previewed or tested without an explicit provider keeps whatever
 * layout its width-driven mode already selected.
 */
val LocalPosWindow = compositionLocalOf { PosWindow(width = 400.dp, height = 800.dp) }
