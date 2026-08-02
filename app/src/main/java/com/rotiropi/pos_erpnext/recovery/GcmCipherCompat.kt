package com.rotiropi.pos_erpnext.recovery

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun initGcmCipher(
    cipher: Cipher,
    mode: Int,
    key: SecretKey,
    iv: ByteArray,
    tagBits: Int,
) {
    cipher.init(mode, key, GCMParameterSpec(tagBits, iv))
}
