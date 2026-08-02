package com.rotiropi.pos_erpnext.recovery

/** Serializes durable prepare admission with token-clearing logout. */
internal object RecoveryLogoutGate {
    val lock = Any()
}
