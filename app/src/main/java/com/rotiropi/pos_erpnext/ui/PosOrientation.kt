package com.rotiropi.pos_erpnext.ui

import android.content.pm.ActivityInfo
import android.content.res.Resources
import com.rotiropi.pos_erpnext.R

/**
 * The orientation the POS asks for on the current device.
 *
 * A phone runs portrait only: a landscape phone window is ~411dp tall, which is not
 * enough for a POS body, so the product does not offer that orientation rather than
 * shipping a clipped one. A tablet is wide enough either way, so rotation is left to
 * the user.
 *
 * The phone/tablet split is `R.bool.pos_lock_portrait`, resolved through the platform's
 * own `sw600dp` qualifier, so the threshold is Android's rather than a dp comparison
 * written here. See [PosWindow] for the separate, finer question of whether a window is
 * tall enough to split a body into two columns.
 */
fun posRequestedOrientation(resources: Resources): Int =
    if (resources.getBoolean(R.bool.pos_lock_portrait)) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
