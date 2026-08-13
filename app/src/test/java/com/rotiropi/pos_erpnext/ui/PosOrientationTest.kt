package com.rotiropi.pos_erpnext.ui

import android.content.pm.ActivityInfo
import com.rotiropi.pos_erpnext.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phones run portrait only; tablets rotate freely.
 *
 * A landscape phone window is ~411dp tall, which is not enough for a POS body, so the
 * product does not offer that orientation at all. The decision is a resource rather
 * than a dp comparison in Kotlin so the platform's own `sw600dp` matching draws the
 * line between a phone and a tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class PosOrientationTest {

    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    @Config(qualifiers = "w411dp-h731dp")
    fun a_phone_locks_portrait() {
        assertEquals(true, resources.getBoolean(R.bool.pos_lock_portrait))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, posRequestedOrientation(resources))
    }

    @Test
    @Config(qualifiers = "sw800dp")
    fun a_tablet_leaves_orientation_to_the_user() {
        assertEquals(false, resources.getBoolean(R.bool.pos_lock_portrait))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, posRequestedOrientation(resources))
    }

    /**
     * A landscape phone still reports the same smallest width, so rotating one does not
     * turn it into a tablet and unlock the orientation it was locked out of.
     */
    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    fun a_rotated_phone_is_not_a_tablet() {
        assertEquals(true, resources.getBoolean(R.bool.pos_lock_portrait))
    }
}
