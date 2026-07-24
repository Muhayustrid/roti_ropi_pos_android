package com.rotiropi.pos_erpnext

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.auth.SignInFragment
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewBindingLifecycleTest {

    @Test
    fun fragment_viewbinding_is_null_after_onDestroyView() {
        val scenario: FragmentScenario<SignInFragment> = launchFragmentInContainer(
            themeResId = R.style.Theme_POSERPNext
        )

        var fragmentRef: SignInFragment? = null
        scenario.onFragment { fragment ->
            fragmentRef = fragment
            assertTrue("Binding should be non-null when view is active", fragment.bindingNullable != null)
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertNull("Binding must be null after onDestroyView", fragmentRef?.bindingNullable)
    }

    @Test
    fun fragment_recreation_does_not_retain_destroyed_view() {
        val scenario: FragmentScenario<SignInFragment> = launchFragmentInContainer(
            themeResId = R.style.Theme_POSERPNext
        )

        var firstViewHash: Int = 0
        scenario.onFragment { fragment ->
            firstViewHash = fragment.requireView().hashCode()
        }

        scenario.recreate()

        scenario.onFragment { fragment ->
            val secondViewHash = fragment.requireView().hashCode()
            assertTrue("Recreated fragment must create a new view instance", firstViewHash != secondViewHash)
            assertTrue("Binding must be valid on recreated fragment", fragment.bindingNullable != null)
        }
    }
}
