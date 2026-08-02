package com.rotiropi.pos_erpnext.ui

import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.rotiropi.pos_erpnext.MainActivity
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = MobilePosApplication::class)
class Task4RootLifecycleTest {
    @Test
    fun activity_uses_native_view_binding_root_with_legacy_compose_bridge() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity.findViewById<View>(R.id.task4_root))
        assertTrue(activity.findViewById<View>(R.id.task4_profile) is View)
        assertTrue(activity.findViewById<View>(R.id.task4_legacy_shell) is ComposeView)

        controller.pause().stop().destroy()
    }
}
