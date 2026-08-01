package com.rotiropi.pos_erpnext.auth

import android.app.Activity
import android.os.Bundle
import com.rotiropi.pos_erpnext.MobilePosApplication

/** Non-exported AppAuth completion sink for cold and warm callback delivery. */
class AuthCompletionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deliver()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let(::setIntent)
        deliver()
    }

    private fun deliver() {
        val received = intent
        if (received.action?.startsWith(ACTION_AUTH_COMPLETION) == true) {
            received.action = ACTION_AUTH_COMPLETION
        } else if (received.action?.startsWith(ACTION_AUTH_CANCEL) == true) {
            received.action = ACTION_AUTH_CANCEL
        } else {
            finish()
            return
        }
        (application as MobilePosApplication).authenticationOwner.handleCompletionAsync(received)
        finish()
    }

    companion object {
        const val ACTION_AUTH_COMPLETION = "com.rotiropi.pos_erpnext.AUTH_COMPLETION"
        const val ACTION_AUTH_CANCEL = "com.rotiropi.pos_erpnext.AUTH_CANCEL"
        const val EXTRA_STATE = "com.rotiropi.pos_erpnext.auth.STATE"
    }
}
