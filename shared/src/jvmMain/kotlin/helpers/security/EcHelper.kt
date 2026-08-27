package org.kde.kdeconnect.helpers.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

actual object EcHelper {
    private var keyPair: KeyPair? = null

    actual fun ensureKeyPair() {
        if (keyPair != null) return
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        keyPair = generator.generateKeyPair()
    }

    actual fun getPublicKey(): PublicKey {
        ensureKeyPair()
        return keyPair!!.public
    }

    actual fun getPrivateKey(): PrivateKey {
        ensureKeyPair()
        return keyPair!!.private
    }
}
